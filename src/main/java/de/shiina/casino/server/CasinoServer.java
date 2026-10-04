package de.shiina.casino.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.shiina.casino.CasinoConfig;
import de.shiina.casino.CasinoContent;
import de.shiina.casino.block.CasinoBlock;
import de.shiina.casino.logic.*;
import de.shiina.casino.menu.CasinoMenu;
import de.shiina.casino.network.CasinoNetwork;
import de.shiina.casino.network.SnapshotDelta;
import de.shiina.casino.storage.CasinoSavedData;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CasinoServer {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private record Position(String dimension, BlockPos pos) {}
    private record ChunkKey(String dimension, long chunk) {}
    private static final Map<MinecraftServer, CasinoServer> INSTANCES = new HashMap<>();
    private static final Gson JSON = new Gson();
    private final MinecraftServer server;
    private final CasinoSavedData data;
    private final SecureRandom random = new SecureRandom();
    private final Map<UUID, Table> tables = new HashMap<>();
    private final Map<Position, Table> positions = new HashMap<>();
    private final Map<ChunkKey, Set<UUID>> chunks = new HashMap<>();
    private final Map<UUID, UUID> participating = new HashMap<>();
    private final PersonalRounds personal = new PersonalRounds();
    private final TreeMap<Long, Set<UUID>> scheduler = new TreeMap<>();
    private boolean stopping;

    private CasinoServer(MinecraftServer server) { this.server = server; data = CasinoSavedData.get(server); data.ledger.recoverAll(); }
    public static CasinoServer get(MinecraftServer server) { return INSTANCES.computeIfAbsent(server, CasinoServer::new); }
    private static long now() { return System.nanoTime() / 1000000L; }

    private final class Table implements Game.Bank {
        final UUID id = UUID.randomUUID();
        final Position position;
        final ServerLevel level;
        final Set<UUID> viewers = new HashSet<>();
        final Map<UUID, String> names = new HashMap<>();
        final Game game;
        Rules limits;
        long scheduled;
        boolean needsCheckpoint;
        long retryAt;
        long visualStarted = -1;
        long visualRound = -1;
        String visualPhase = "";

        Table(ServerLevel level, BlockPos pos, String kind, Rules rules) {
            this.level = level; position = new Position(level.dimension().location().toString(), pos.immutable()); limits = rules;
            game = switch (kind) {
                case "blackjack" -> new BlackjackGame(this, rules, random);
                case "poker" -> new PokerGame(this, rules, random);
                case "roulette" -> new RouletteGame(this, rules, random);
                case "wheel" -> new WheelGame(this, rules, random);
                default -> throw new IllegalArgumentException("casino.error.action");
            };
        }
        @Override public boolean available(UUID player) { UUID active = participating.get(player); return active == null || active.equals(id); }
        @Override public boolean debit(UUID player, long amount) {
            ServerPlayer online = server.getPlayerList().getPlayer(player);
            if (online == null || !near(online)) return false;
            long reserved = data.ledger.pending().values().stream().mapToLong(obligation -> obligation.recovery().getOrDefault(player, 0L)).sum();
            if (data.ledger.balance(player) + reserved > Money.LIMIT - Money.multiply(amount, 1000)) return false;
            names.put(player, online.getGameProfile().getName());
            return ChipInventory.debit(online, amount);
        }
        @Override public void credit(UUID player, long amount) { data.ledger.credit(player, amount); automaticClaim(player); }
        @Override public void hold(UUID transaction, String game, long round, Map<UUID, Long> recovery) {
            data.ledger.hold(transaction, id + "@" + position.dimension + ":" + position.pos.toShortString(), game, round, recovery);
            if (game.startsWith("roulette:result=") || game.startsWith("wheel:segment=")) needsCheckpoint = true;
        }
        @Override public void settle(UUID transaction) {
            Ledger.Obligation obligation = data.ledger.pending().get(transaction);
            if (data.ledger.settle(transaction) && obligation != null) obligation.recovery().keySet().forEach(CasinoServer.this::automaticClaim);
        }
        @Override public int seat(UUID player) { return CasinoSeats.get(server).index(player,position.dimension,position.pos); }
        @Override public void completed(UUID player, PersonalRounds.Result result) { personal.complete(player, result); }
        @Override public void rememberBet(UUID player, String game, long amount) { personal.remember(player, game, amount); }
        @Override public void rememberWagers(UUID player, List<PersonalRounds.Wager> wagers) { personal.rememberWagers(player, wagers); }
        @Override public List<PersonalRounds.Wager> lastWagers(UUID player) { return personal.lastWagers(player); }
        @Override public String name(UUID player) { return names.getOrDefault(player, "?"); }
        boolean near(ServerPlayer player) {
            return player.serverLevel() == level && player.isAlive()
                    && player.distanceToSqr(position.pos.getX() + .5, position.pos.getY() + .5, position.pos.getZ() + .5) <= limits.range() * limits.range();
        }
    }

    public void open(ServerPlayer player, BlockPos pos, String kind) {
        if (!player.isAlive() || !player.canInteractWithBlock(pos, 0) || !player.level().hasChunkAt(pos)
                || !(player.level().getBlockState(pos).getBlock() instanceof CasinoBlock block) || !block.kind.equals(kind)) return;
        if (player.level().getBlockState(pos).getValue(CasinoBlock.EXPANDED)
                && !de.shiina.casino.block.StationStructure.complete(player.level(),pos,player.level().getBlockState(pos))) return;
        try {
            personal.open(player.getUUID());
            Rules rules = CasinoConfig.snapshot();
            Position position = new Position(player.level().dimension().location().toString(), pos.immutable());
            Table table = positions.get(position);
            if (!kind.equals("exchange")) {
                boolean enabled = switch (kind) {
                    case "blackjack" -> rules.blackjack(); case "poker" -> rules.poker();
                    case "roulette" -> rules.roulette(); case "wheel" -> rules.fortune(); default -> false;
                };
                if (!enabled && table == null) throw new IllegalArgumentException("casino.error.disabled");
                if (table == null) {
                    if (tables.size() >= rules.maxTables()) throw new IllegalArgumentException("casino.error.tables");
                    table = new Table(player.serverLevel(), pos, kind, rules);
                    tables.put(table.id, table); positions.put(position, table);
                    chunks.computeIfAbsent(chunkKey(position), key -> new HashSet<>()).add(table.id);
                }
                Game openedGame = table.game;
                long spectators = table.viewers.stream().filter(viewer -> !openedGame.contains(viewer)).count();
                if (!table.game.contains(player.getUUID()) && !table.viewers.contains(player.getUUID()) && spectators >= rules.spectators())
                    if (table.viewers.size() >= rules.spectators() + (kind.equals("blackjack") || kind.equals("poker") ? 6 : 16))
                        throw new IllegalArgumentException("casino.error.spectators");
            }
            UUID id = table == null ? UUID.randomUUID() : table.id;
            player.openMenu(new SimpleMenuProvider((menuId, inventory, owner) -> new CasinoMenu(menuId, inventory, id, pos, kind),
                    Component.translatable("casino.title." + kind)), buffer -> { buffer.writeUUID(id); buffer.writeBlockPos(pos); buffer.writeUtf(kind, 16); });
            if (table != null) { table.viewers.add(player.getUUID()); schedule(table); }
            send(player, table, "", true);
        } catch (IllegalArgumentException exception) { player.displayClientMessage(Component.translatable(exception.getMessage()), true); }
    }

    public void unwatch(ServerPlayer player, UUID id) {
        Table table = tables.get(id);
        if (table == null) return;
        table.viewers.remove(player.getUUID()); cleanup(table);
    }

    public void action(ServerPlayer player, CasinoNetwork.Action packet) {
        if (!(player.containerMenu instanceof CasinoMenu menu) || menu.containerId != packet.menu()
                || !menu.table.equals(packet.table())) return;
        Table table = tables.get(menu.table);
        if (!menu.stillValid(player)) { send(player, table, "casino.error.unavailable"); return; }
        if (!menu.actionGate.accept(packet.sequence(), server.getTickCount())) {
            if (packet.sequence() > menu.acknowledgedSequence && menu.lastRejectionTick != server.getTickCount()) {
                menu.lastRejectionTick = server.getTickCount(); menu.acknowledgedSequence = packet.sequence();
                send(player, table, "casino.error.fast");
            }
            return;
        }
        menu.acknowledgedSequence = packet.sequence();
        try {
            Money.checked(packet.amount());
            if (menu.kind.equals("exchange")) exchange(player, menu, packet);
            else {
                if (table == null) throw new IllegalArgumentException("casino.error.unavailable");
                if (!table.near(player)) throw new IllegalArgumentException("casino.error.distance");
                if (!checkpoint(table)) { send(player, table, "casino.error.save"); schedule(table); return; }
                if (table.game.deadline() > 0 && now() >= table.game.deadline()) table.game.timeout(now());
                table.game.nextRules(CasinoConfig.snapshot());
                table.limits = table.game.rules();
                if (player.level().getBlockState(menu.pos).getValue(CasinoBlock.EXPANDED)
                        && ((menu.kind.equals("blackjack") || menu.kind.equals("poker")) && !packet.action().equals("leave")
                            || menu.kind.equals("roulette") && (packet.action().startsWith("roulette_") || packet.action().equals("repeat")))
                        && !CasinoSeats.get(server).at(player,menu.pos)) throw new IllegalArgumentException("casino.error.sit");
                table.game.guarded(player.getUUID(), packet.round(), packet.revision(), packet.action(), packet.amount(), packet.option(), now());
                refresh(table); schedule(table);
                if (!checkpoint(table)) { send(player, table, "casino.error.save"); schedule(table); return; }
            }
            String confirmation = switch (packet.action()) {
                case "bet" -> "casino.status.bet";
                case "repeat" -> "casino.status.repeat";
                case "spin" -> "casino.status.spin";
                case "cancel" -> "casino.status.cancel";
                case "leave" -> "casino.status.leave";
                case "buy", "sell" -> "casino.status.exchange";
                case "claim" -> "casino.status.claim";
                default -> packet.action().startsWith("roulette_") ? "casino.status.bet" : "casino.status.action";
            };
            send(player, table, confirmation);
            if (table != null) broadcast(table);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            String error = exception.getMessage();
            error = error != null && error.startsWith("casino.") ? error : "casino.error.amount";
            if (table != null) {
                refresh(table);
                if (!checkpoint(table)) { schedule(table); send(player, table, "casino.error.save"); return; }
                send(player, table, error);
                broadcast(table); schedule(table);
            } else {
                send(player, null, error);
            }
        }
    }

    private void exchange(ServerPlayer player, CasinoMenu menu, CasinoNetwork.Action packet) {
        if (packet.round() != 0 || packet.revision() != menu.exchangeRevision) throw new IllegalArgumentException("casino.error.stale");
        int rate = CasinoConfig.snapshot().exchangeRate();
        if (packet.option() != rate) throw new IllegalArgumentException("casino.error.stale");
        long quantity = packet.amount();
        if (!packet.action().equals("claim") && (quantity < 1 || quantity > 2304)) throw new IllegalArgumentException("casino.error.amount");
        boolean result;
        switch (packet.action()) {
            case "buy" -> {
                if (ChipInventory.count(player, Items.DIAMOND) < quantity) throw new IllegalArgumentException("casino.error.diamonds");
                result = ChipInventory.exchange(player, Items.DIAMOND, quantity, CasinoContent.CHIP.get(), Money.multiply(rate, quantity));
            }
            case "sell" -> {
                long cost = Money.multiply(rate, quantity);
                if (ChipInventory.count(player, CasinoContent.CHIP.get()) < cost) throw new IllegalArgumentException("casino.error.chips");
                result = ChipInventory.exchange(player, CasinoContent.CHIP.get(), cost, Items.DIAMOND, quantity);
            }
            case "claim" -> {
                if (data.ledger.balance(player.getUUID()) == 0) throw new IllegalArgumentException("casino.error.balance");
                if (claim(player) == 0) throw new IllegalArgumentException("casino.error.inventory");
                menu.exchangeRevision++; return;
            }
            default -> throw new IllegalArgumentException("casino.error.action");
        }
        if (!result) throw new IllegalArgumentException("casino.error.inventory");
        menu.exchangeRevision++;
    }

    private long claim(ServerPlayer player) { return data.ledger.claim(player.getUUID(), 2304, amount -> ChipInventory.give(player, amount)); }
    private void automaticClaim(UUID id) { ServerPlayer player = server.getPlayerList().getPlayer(id); if (player != null) claim(player); }

    private void refresh(Table table) {
        participating.entrySet().removeIf(entry -> entry.getValue().equals(table.id));
        if (table.game.funded()) for (UUID player : table.game.players()) if (table.game.contains(player)) participating.put(player, table.id);
        table.names.keySet().removeIf(player -> !table.game.players().contains(player) && !table.viewers.contains(player));
    }

    private void send(ServerPlayer player, Table table, String error) {
        send(player, table, error, error != null && error.startsWith("casino.error."));
    }
    private void send(ServerPlayer player, Table table, String error, boolean full) {
        if (!(player.containerMenu instanceof CasinoMenu menu)) return;
        if (table != null && !menu.table.equals(table.id)) return;
        JsonObject state = table == null ? new JsonObject() : JSON.toJsonTree(table.game.view(player.getUUID(), now())).getAsJsonObject();
        if (table != null && player.level().getBlockState(menu.pos).getBlock() instanceof CasinoBlock
                && player.level().getBlockState(menu.pos).getValue(CasinoBlock.EXPANDED)
                && (menu.kind.equals("blackjack") || menu.kind.equals("poker") || menu.kind.equals("roulette")) && !CasinoSeats.get(server).at(player,menu.pos)) {
            var actions=state.getAsJsonArray("actions");
            for(int i=actions.size()-1;i>=0;i--) if(!actions.get(i).getAsString().equals("leave") && !actions.get(i).getAsString().equals("cancel")) actions.remove(i);
        }
        String feedback = error == null ? menu.error : error;
        state.add("history", JSON.toJsonTree(personal.history(player.getUUID())));
        state.addProperty("lastBet", personal.lastBet(player.getUUID(), menu.kind));
        state.add("lastWagers", JSON.toJsonTree(personal.lastWagers(player.getUUID())));
        state.addProperty("ack", menu.acknowledgedSequence);
        state.addProperty("exchangeRevision", menu.exchangeRevision);
        state.addProperty("smallBlind", table == null ? 0 : table.game.rules().smallBlind());
        state.addProperty("bigBlind", table == null ? 0 : table.game.rules().bigBlind());
        state.addProperty("available", table == null || table.available(player.getUUID()));
        state.addProperty("near", table == null || table.near(player));
        if (table != null && (!table.available(player.getUUID()) || !table.near(player))) {
            var actions=state.getAsJsonArray("actions");
            for(int i=actions.size()-1;i>=0;i--) if(List.of("bet","join","spin","roulette","repeat").contains(actions.get(i).getAsString())) actions.remove(i);
        }
        state.addProperty("updateSequence", menu.updateSequence);
        state.addProperty("seated", table == null || !player.level().getBlockState(menu.pos).hasProperty(CasinoBlock.EXPANDED)
                || !player.level().getBlockState(menu.pos).getValue(CasinoBlock.EXPANDED) || CasinoSeats.get(server).at(player, menu.pos));
        if (menu.kind.equals("exchange")) {
            state.addProperty("diamonds", ChipInventory.count(player, Items.DIAMOND));
            state.addProperty("maxBuy", ChipInventory.maximumExchange(player, Items.DIAMOND, 1, CasinoContent.CHIP.get(), CasinoConfig.RATE.get()));
            state.addProperty("maxSell", ChipInventory.maximumExchange(player, CasinoContent.CHIP.get(), CasinoConfig.RATE.get(), Items.DIAMOND, 1));
            state.addProperty("claimCapacity", ChipInventory.capacity(player, CasinoContent.CHIP.get()));
        }
        long chips = ChipInventory.count(player, CasinoContent.CHIP.get());
        long balance = data.ledger.balance(player.getUUID()); int rate = CasinoConfig.RATE.get();
        full |= menu.lastState == null;
        JsonObject update = full ? state : SnapshotDelta.between(menu.lastState, state);
        if (!full && update.isEmpty() && menu.balance == balance && menu.chips == chips && menu.rate == rate && menu.error.equals(feedback)) return;
        state.addProperty("updateSequence", ++menu.updateSequence);
        update = full ? state : SnapshotDelta.between(menu.lastState, state);
        PacketDistributor.sendToPlayer(player, new CasinoNetwork.State(menu.containerId, menu.table, full, now(), JSON.toJson(update), balance, chips, rate, feedback));
        menu.lastState = state; menu.balance = balance; menu.chips = chips; menu.rate = rate; menu.error = feedback;
    }
    public void syncMenu(ServerPlayer player) {
        if (player.containerMenu instanceof CasinoMenu menu && menu.stillValid(player)) send(player, tables.get(menu.table), null);
    }
    private void syncVisual(Table table) {
        if (!(table.level.getBlockEntity(table.position.pos) instanceof de.shiina.casino.block.WheelBlockEntity wheel)) return;
        Game.View view=table.game.view(new UUID(0,0),now());
        if(table.visualPhase.equals(view.phase()) && table.visualRound==view.round() && table.visualStarted==view.startedMillis()) return;
        table.visualPhase=view.phase(); table.visualRound=view.round(); table.visualStarted=view.startedMillis();
        boolean spinning=view.phase().equals("spinning");
        int duration=table.game.kind().equals("roulette")?100:80;
        long start=table.level.getGameTime()-Math.max(0,(now()-view.startedMillis())/50);
        wheel.animation(view.round(),spinning,start,spinning?duration:0,view.result()<0?wheel.result:view.result());
    }
    private void broadcast(Table table) {
        syncVisual(table);
        for (UUID viewer : List.copyOf(table.viewers)) {
            ServerPlayer player = server.getPlayerList().getPlayer(viewer);
            if (player == null || !(player.containerMenu instanceof CasinoMenu menu) || !menu.table.equals(table.id)) table.viewers.remove(viewer);
            else send(player, table, null);
        }
    }

    private void unschedule(Table table) {
        if (table.scheduled == 0) return;
        Set<UUID> bucket = scheduler.get(table.scheduled);
        if (bucket != null) { bucket.remove(table.id); if (bucket.isEmpty()) scheduler.remove(table.scheduled); }
        table.scheduled = 0;
    }
    private boolean checkpoint(Table table) {
        if (!table.needsCheckpoint) return true;
        if (now() < table.retryAt) return false;
        try {
            data.checkpoint(server); table.needsCheckpoint = false; table.retryAt = 0; return true;
        } catch (java.io.IOException exception) {
            table.retryAt = now() + 5000;
            LOGGER.error("ShiinaCasino: Ergebnis-Checkpoint fehlgeschlagen; Runde bleibt gebunden.", exception);
            return false;
        }
    }
    private void schedule(Table table) {
        unschedule(table);
        if (!table.game.funded() && table.game.deadline() == 0) return;
        long time = now();
        long next = time + (table.game.funded() ? 1000 : 10000);
        if (table.game.deadline() > 0) next = Math.min(next, table.game.deadline());
        next = Math.max(next, table.retryAt);
        table.scheduled = next; scheduler.computeIfAbsent(next, key -> new HashSet<>()).add(table.id);
    }

    private void tick() {
        if (stopping) return;
        long time = now();
        while (!scheduler.isEmpty() && scheduler.firstKey() <= time) {
            Set<UUID> due = scheduler.pollFirstEntry().getValue();
            for (UUID id : due) {
                Table table = tables.get(id); if (table == null) continue;
                table.scheduled = 0;
                if (!checkpoint(table)) { schedule(table); continue; }
                if (!table.level.hasChunkAt(table.position.pos)
                        || !(table.level.getBlockState(table.position.pos).getBlock() instanceof CasinoBlock block) || !block.kind.equals(table.game.kind())) {
                    remove(table.position.dimension, table.position.pos); continue;
                }
                long before = table.game.revision();
                for (UUID participant : table.game.players()) {
                    ServerPlayer player = server.getPlayerList().getPlayer(participant);
                    if (player == null || !table.near(player)) table.game.depart(participant, time);
                }
                table.game.nextRules(CasinoConfig.snapshot());
                table.limits = table.game.rules();
                table.game.timeout(time); refresh(table);
                if (table.game.revision() != before && checkpoint(table)) broadcast(table);
                if (!cleanup(table)) schedule(table);
            }
        }
    }

    private boolean cleanup(Table table) {
        if (!table.game.funded() && table.viewers.isEmpty() && !CasinoSeats.get(server).occupied(table.position.dimension,table.position.pos)) { remove(table.position.dimension, table.position.pos); return true; }
        return false;
    }
    public boolean active(String dimension, BlockPos pos) { Table table = positions.get(new Position(dimension, pos)); return table != null && table.game.funded(); }
    public boolean canSit(ServerPlayer player,BlockPos pos,int seat) {
        Position position=new Position(player.level().dimension().location().toString(),pos);
        Table table=positions.get(position);
        UUID active=participating.get(player.getUUID());
        if(active!=null && (table==null || !active.equals(table.id))) return false;
        if(table==null) {
            var block=player.level().getBlockState(pos).getBlock();
            Rules rules=CasinoConfig.snapshot();
            if(!(block instanceof CasinoBlock casino) || tables.size()>=rules.maxTables()
                    || casino.kind.equals("blackjack") && !rules.blackjack() || casino.kind.equals("poker") && !rules.poker() || casino.kind.equals("roulette") && !rules.roulette()) return false;
            return true;
        }
        if(!table.game.kind().equals("blackjack") && !table.game.kind().equals("poker")) return true;
        for(Game.Seat hand:table.game.view(player.getUUID(),now()).seats()) {
            if(hand.id().equals(player.getUUID().toString()) && hand.seat()!=seat) return false;
            if(hand.seat()==seat && !hand.id().equals(player.getUUID().toString())) return false;
        }
        return true;
    }
    public void seatReleased(UUID player,String dimension,BlockPos pos) {
        Table table=positions.get(new Position(dimension,pos));
        if(table==null || stopping) return;
        table.game.depart(player,now()); refresh(table); broadcast(table); if(!cleanup(table)) schedule(table);
    }
    public int registeredTables() { return tables.size(); }
    private static ChunkKey chunkKey(Position position) { return new ChunkKey(position.dimension, net.minecraft.world.level.ChunkPos.asLong(position.pos.getX() >> 4, position.pos.getZ() >> 4)); }

    public void remove(String dimension, BlockPos pos) {
        Table table = positions.remove(new Position(dimension, pos)); if (table == null) return;
        if(table.level.hasChunkAt(pos) && table.level.getBlockEntity(pos) instanceof de.shiina.casino.block.WheelBlockEntity wheel)
            wheel.animation(wheel.round,false,wheel.startedTick,0,wheel.result);
        table.game.close(); refresh(table); unschedule(table); tables.remove(table.id);
        ChunkKey key = chunkKey(table.position);
        Set<UUID> indexed = chunks.get(key);
        if (indexed != null) { indexed.remove(table.id); if (indexed.isEmpty()) chunks.remove(key); }
        for (UUID viewer : List.copyOf(table.viewers)) {
            ServerPlayer player = server.getPlayerList().getPlayer(viewer);
            if (player != null && player.containerMenu instanceof CasinoMenu menu && menu.table.equals(table.id)) player.closeContainer();
        }
        table.viewers.clear(); table.names.clear();
    }

    @SubscribeEvent public static void started(ServerStartedEvent event) { get(event.getServer()); }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) { get(event.getServer()).tick(); }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        CasinoServer manager = INSTANCES.get(event.getServer());
        if (manager != null) {
            manager.stopping = true;
            CasinoSeats.get(event.getServer()).releaseAll();
            for (Table table : new ArrayList<>(manager.tables.values())) table.game.close();
            manager.tables.clear(); manager.scheduler.clear(); manager.participating.clear(); manager.positions.clear(); manager.chunks.clear();
            manager.personal.clear();
            manager.data.setDirty();
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { INSTANCES.remove(event.getServer()); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CasinoServer manager = get(player.server);
        manager.personal.forget(player.getUUID());
        UUID tableId = manager.participating.get(player.getUUID());
        Table table = manager.tables.get(tableId);
        if (table != null) { table.game.depart(player.getUUID(), now()); manager.refresh(table); manager.broadcast(table); manager.schedule(table); }
        if (player.containerMenu instanceof CasinoMenu menu) manager.unwatch(player, menu.table);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) get(player.server).personal.open(player.getUUID());
    }
    private static boolean protectedPart(ServerLevel level,BlockPos pos) {
        BlockPos anchor=de.shiina.casino.block.StationStructure.anchor(level,pos);
        return anchor!=null && get(level.getServer()).active(level.dimension().location().toString(),anchor);
    }
    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && protectedPart(level,event.getPos())) {
            event.setCanceled(true); event.getPlayer().displayClientMessage(Component.translatable("casino.error.active"), true);
        }
    }
    @SubscribeEvent public static void explosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level) {
            CasinoServer manager = get(level.getServer());
            event.getAffectedBlocks().removeIf(pos -> protectedPart(level,pos));
        }
    }
    @SubscribeEvent public static void unload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        CasinoServer manager = get(level.getServer());
        ChunkKey key = new ChunkKey(level.dimension().location().toString(), event.getChunk().getPos().toLong());
        for (UUID id : List.copyOf(manager.chunks.getOrDefault(key, Set.of()))) {
            Table table = manager.tables.get(id); if (table != null) { CasinoSeats.get(level.getServer()).releaseStation(table.position.dimension,table.position.pos); manager.remove(table.position.dimension, table.position.pos); }
        }
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("shiinacasino")
                .then(Commands.literal("claim").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    long delivered = get(player.server).claim(player);
                    context.getSource().sendSuccess(() -> Component.translatable("casino.command.claim", delivered), false); return 1;
                }))
                .then(Commands.literal("balance").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException(); long balance = get(player.server).data.ledger.balance(player.getUUID());
                    context.getSource().sendSuccess(() -> Component.translatable("casino.command.balance", balance), false); return 1;
                }))
                .then(Commands.literal("status").requires(source -> source.hasPermission(2)).executes(context -> {
                    CasinoServer manager = get(context.getSource().getServer());
                    context.getSource().sendSuccess(() -> Component.translatable("casino.command.status", manager.tables.size(), manager.participating.size(),
                            manager.data.ledger.pending().size(), manager.data.ledger.balances().size()), false); return 1;
                })));
    }
}
