package de.shiina.casino.client;

import de.shiina.casino.logic.Game;
import de.shiina.casino.logic.Roulette;
import de.shiina.casino.logic.PersonalRounds;
import de.shiina.casino.menu.CasinoMenu;
import de.shiina.casino.network.CasinoNetwork;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class CasinoScreen extends AbstractContainerScreen<CasinoMenu> {
    private static final net.minecraft.resources.ResourceLocation FELT_TEXTURE = texture("felt");
    private static final net.minecraft.resources.ResourceLocation CARD_BACK_TEXTURE = texture("card_back");
    private static final net.minecraft.resources.ResourceLocation BUTTON_TEXTURE = texture("button");
    private static final net.minecraft.resources.ResourceLocation BUTTON_HOVER_TEXTURE = texture("button_hover");
    private static final net.minecraft.resources.ResourceLocation BUTTON_DISABLED_TEXTURE = texture("button_disabled");
    private static net.minecraft.resources.ResourceLocation texture(String name) {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("shiinacasino", "textures/gui/" + name + ".png");
    }
    private record Control(int x, int y, int width, int height, String text, String action, int option, boolean enabled, String reason) {}
    private final int WIDTH, HEIGHT;
    private static final int GOLD = 0xffd9b45b, WOOD = 0xff291c15, FELT = 0xff073c28, WHITE = 0xfff8eed6;
    private final List<Control> controls = new ArrayList<>();
    private record CardMotion(int previous, long started) {}
    private final Map<String, CardMotion> cardMotions = new HashMap<>();
    private float scale = 1;
    private int originX, originY;
    private double mouseLocalX, mouseLocalY;
    private String amount = "20";
    private boolean editing, replaceAmount, pending, initialized;
    private long sentAt;
    private String page = "";
    private int scroll;
    private long resultShownAt;
    private String resultKey = "";
    private long noticeUntil;
    private String notice = "";
    private static final com.google.gson.Gson JSON = new com.google.gson.Gson();
    private com.google.gson.JsonElement cachedHistorySource;
    private List<PersonalRounds.Result> cachedHistory=List.of();

    public CasinoScreen(CasinoMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); WIDTH=420; HEIGHT=326; }
    public void received(Game.View previous) {
        if (menu.acknowledgedSequence >= menu.sequence) pending = false;
        var result = latestResult();
        String nextKey = result == null ? "" : result.transaction() + ":" + result.round();
        if (!nextKey.equals(resultKey)) {
            resultShownAt = resultKey.isEmpty() ? 0 : clock();
            if (previous != null && resultKey.isEmpty()) resultShownAt = clock();
            resultKey = nextKey;
        }
        Game.View next = menu.view;
        if (next == null) return;
        if (previous == null || previous.round() != next.round()) cardMotions.clear();
        if (previous == null || CasinoClientConfig.REDUCED.get()) return;
        cardMotions.entrySet().removeIf(entry -> clock() + menu.serverOffset - entry.getValue().started > 1000);
        animateCards("dealer", previous.dealer(), next.dealer(), next.changedMillis());
        animateCards("board", previous.board(), next.board(), next.changedMillis());
        for (Game.Seat seat : next.seats()) {
            Game.Seat before = previous == null ? null : previous.seats().stream().filter(old -> old.id().equals(seat.id())).findFirst().orElse(null);
            animateCards("seat" + seat.seat(), before == null ? List.of() : before.cards(), seat.cards(), next.changedMillis());
        }
    }
    private void animateCards(String group, List<Integer> before, List<Integer> after, long started) {
        if (clock() + menu.serverOffset - started > 1000) return;
        for (int index = 0; index < after.size(); index++) {
            int previous = index < before.size() ? before.get(index) : -2;
            if (previous != after.get(index)) cardMotions.put(group + ":" + index, new CardMotion(previous, started));
        }
    }
    private String tr(String key, Object... values) { return Component.translatable(key, values).getString(); }
    private String ownId() { return minecraft.player == null ? "" : minecraft.player.getUUID().toString(); }
    private long amount() { try { return Long.parseLong(amount); } catch (NumberFormatException exception) { return 0; } }
    private long amountMaximum() {
        if(menu.view==null) return 1000000;
        if(!menu.kind.equals("poker")) return menu.view.maxBet();
        return menu.view.seats().stream().filter(seat->seat.id().equals(ownId())).mapToLong(seat->seat.stack()+seat.bet()).findFirst().orElse(menu.view.buyInMax());
    }
    private long clock() { return System.nanoTime() / 1000000L; }
    private long remaining() { return menu.view == null ? 0 : Math.max(0, menu.view.deadlineMillis() - clock() - menu.serverOffset); }
    private long data(String key) { return menu.lastState == null || !menu.lastState.has(key) ? 0 : menu.lastState.get(key).getAsLong(); }
    private List<PersonalRounds.Result> history() {
        if (menu.lastState == null) return List.of();
        var source=menu.lastState.get("history");
        if (source != cachedHistorySource) { cachedHistory=List.of(JSON.fromJson(source, PersonalRounds.Result[].class));cachedHistorySource=source; }
        return cachedHistory;
    }
    private PersonalRounds.Result latestResult() { return history().stream().filter(result -> result.game().equals(menu.kind)).findFirst().orElse(null); }
    private boolean local(String action) { return List.of("close", "help", "history", "settings", "back", "bets", "amount", "minus", "plus", "last", "sounds", "volumeDown", "volumeUp", "reduced").contains(action); }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        scale = Math.min(1.4F, Math.min((width - 8F) / WIDTH, (height - 8F) / HEIGHT));
        originX = (int) ((width - WIDTH * scale) / 2); originY = (int) ((height - HEIGHT * scale) / 2);
        mouseLocalX = (mouseX - originX) / scale; mouseLocalY = (mouseY - originY) / scale;
        if (pending && clock() - sentAt > 2000) pending = false;
        if (!initialized && menu.view != null) {
            initialized = true;
            amount = Long.toString(menu.kind.equals("poker") ? Math.max(menu.view.buyInMin(), Math.min(200, menu.view.buyInMax()))
                    : Math.min(menu.view.maxBet(), Math.max(20, menu.view.minBet())));
        }
        controls.clear();
        graphics.pose().pushPose(); graphics.pose().translate(originX, originY, 0); graphics.pose().scale(scale, scale, 1);
        graphics.fill(0, 0, WIDTH, HEIGHT, 0xff101010);
        graphics.fill(1, 1, WIDTH - 1, HEIGHT - 1, 0xffdeded8);
        graphics.fill(3, 3, WIDTH - 3, HEIGHT - 3, 0xff555851);
        graphics.fill(4, 4, WIDTH - 4, 20, 0xffa5a7a0);
        graphics.fill(5, 21, WIDTH - 5, HEIGHT - 5, GOLD);
        graphics.fill(6, 22, WIDTH - 6, HEIGHT - 6, FELT);
        for (int row = 22; row < HEIGHT - 6; row += 32) for (int column = 6; column < WIDTH - 6; column += 32)
            graphics.blit(FELT_TEXTURE, column, row, 0F, 0F, Math.min(32, WIDTH - 6 - column), Math.min(32, HEIGHT - 6 - row), 32, 32);
        text(graphics, shortText(title.getString(), WIDTH-170), 9, 8, 0xff111711);
        text(graphics, tr("casino.chips", menu.chips, menu.balance), 10, 23, WHITE);
        button(graphics, WIDTH-29, 5, 21, 13, "X", "close", 0, true);
        button(graphics, WIDTH-54, 5, 21, 13, "?", "help", 0, true);
        button(graphics, WIDTH-95, 5, 37, 13, tr("casino.settings"), "settings", 0, true);
        button(graphics, WIDTH-153, 5, 54, 13, tr("casino.history"), "history", 0, true);
        if (menu.view != null) {
            Game.View view = menu.view;
            String status = tr("casino.phase." + view.phase());
            Game.Seat active = view.seats().stream().filter(Game.Seat::active).findFirst().orElse(null);
            if (active != null) status += " | " + tr(active.id().equals(ownId()) ? "casino.turn.self" : "casino.turn.other", active.name());
            if (remaining() > 0) status += ": " + ((remaining() + 999) / 1000) + " s";
            text(graphics, shortText(status, WIDTH-20), 10, 36, GOLD);
        }
        if (!page.isEmpty()) subview(graphics);
        else if (menu.kind.equals("exchange")) {
            if(menu.lastState==null) center(graphics,tr("casino.loading"),WIDTH/2,HEIGHT/2,WHITE);
            else exchange(graphics);
        }
        else if (menu.view == null) center(graphics, tr("casino.loading"), WIDTH/2, HEIGHT/2, WHITE);
        else {
            Game.View view = menu.view;
            switch (menu.kind) {
                case "blackjack" -> blackjack(graphics, view);
                case "poker" -> poker(graphics, view);
                case "roulette" -> roulette(graphics, view);
                case "wheel" -> wheel(graphics, view);
                default -> {}
            }
            if(!menu.kind.equals("wheel")) stake(graphics);
            personalResult(graphics);
        }
        String feedback=pending?"casino.status.pending":clock()<noticeUntil?notice:clock()-menu.feedbackAt<7000?menu.error:"";
        if (!feedback.isEmpty()) center(graphics, shortText(tr(feedback),WIDTH-20), WIDTH/2, HEIGHT-13, feedback.startsWith("casino.error.") ? 0xffffb5a0 : 0xffb9e6be);
        tooltip(graphics);
        graphics.pose().popPose();
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {}
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {}

    private void text(GuiGraphics graphics, String text, int x, int y, int color) { graphics.drawString(font, text, x, y, color, false); }
    private void center(GuiGraphics graphics, String text, int x, int y, int color) { text(graphics, text, x - font.width(text) / 2, y, color); }
    private String shortText(String value, int width) { return font.plainSubstrByWidth(value, width); }

    private void button(GuiGraphics graphics, int x, int y, int width, int height, String label, String action, int option, boolean enabled) {
        enabled &= !pending || local(action);
        boolean hover = mouseLocalX >= x && mouseLocalX < x + width && mouseLocalY >= y && mouseLocalY < y + height;
        boolean field = action.startsWith("roulette_");
        int background = enabled ? hover ? 0xff93958e : 0xff71746d : 0xff3c423e;
        if (field) {
            if (action.equals("roulette_red") || action.equals("roulette_number") && option > 0 && Roulette.red(option)) background = enabled ? 0xffa52e32 : 0xff592a2d;
            else if (action.equals("roulette_black") || action.equals("roulette_number") && option > 0) background = enabled ? 0xff24252a : 0xff202a24;
            else background = enabled ? hover ? 0xff286f46 : 0xff10492e : 0xff173d2b;
        }
        graphics.fill(x, y, x + width, y + height, enabled ? 0xffc6c9c1 : 0xff535b54);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, background);
        graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, enabled ? 0xff393d39 : 0xff2a302b);
        graphics.fill(x + width - 2, y + 1, x + width - 1, y + height - 1, enabled ? 0xff393d39 : 0xff2a302b);
        if (!field) graphics.blit(enabled ? hover ? BUTTON_HOVER_TEXTURE : BUTTON_TEXTURE : BUTTON_DISABLED_TEXTURE,
                x, y, width, height, 0F, 0F, 128, 20, 128, 20);
        center(graphics, shortText(label, width - 4), x + width / 2, y + (height - 8) / 2, enabled ? WHITE : 0xff859184);
        controls.add(new Control(x, y, width, height, label, action, option, enabled, enabled ? "" : disabledReason(action, option)));
    }

    private void actionButton(GuiGraphics graphics, int x, int y, int width, String action, Game.View view) {
        boolean enabled = view.actions().contains(action);
        if (action.equals("bet") || action.equals("spin")) enabled &= amount() >= view.minBet() && amount() <= view.maxBet()
                && menu.chips >= amount() && (!action.equals("bet") || amount() % 2 == 0);
        if (action.equals("join")) enabled &= amount() >= view.buyInMin() && amount() <= view.buyInMax() && menu.chips >= amount();
        if (action.equals("double")) enabled &= view.seats().stream().filter(seat -> seat.id().equals(ownId())).allMatch(seat -> menu.chips >= seat.bet());
        if (action.equals("raise")) {
            Game.Seat self = view.seats().stream().filter(seat -> seat.id().equals(ownId())).findFirst().orElse(null);
            enabled &= self != null && amount() >= view.minimumRaise() && amount() <= self.stack() + self.bet();
        }
        actionLabel(graphics, x, y, width, action, tr("casino.action." + action), enabled);
    }
    private void actionLabel(GuiGraphics graphics, int x, int y, int width, String action, String label, boolean enabled) {
        button(graphics, x, y, width, 20, label, action, 0, enabled);
    }

    private void stake(GuiGraphics graphics) {
        text(graphics, tr(menu.kind.equals("poker") ? "casino.amount.poker" : "casino.amount.bet"), 10, 261, WHITE);
        button(graphics, 148, 256, 100, 20, amount + (editing ? "_" : ""), "amount", 0, true);
        button(graphics, 255, 256, 28, 20, "-", "minus", 0, true);
        button(graphics, 288, 256, 28, 20, "+", "plus", 0, true);
        if (menu.kind.equals("blackjack")) button(graphics, 323, 256, 87, 20, tr("casino.last.bet"), "last", 0, data("lastBet") > 0);
        else if (menu.view != null) text(graphics, tr("casino.round", menu.view.round()), 328, 261, GOLD);
    }

    private void seat(GuiGraphics graphics, Game.Seat seat, int x, int y, int width, boolean showCards) {
        graphics.fill(x, y, x + width, y + 55, seat.active() ? GOLD : 0xff698978);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 54, seat.active() ? 0xff226749 : 0xff123d30);
        String name = seat.name() + (seat.seat() == menu.view.button() ? " [D]" : "");
        text(graphics, shortText((seat.active() ? "> " : "") + name, width - 5), x + 3, y + 3, seat.active() ? GOLD : WHITE);
        String info = menu.kind.equals("poker") ? seat.stack() + " / " + seat.bet() : tr("casino.bet", seat.bet());
        if (menu.kind.equals("blackjack") && !seat.cards().isEmpty()) info += " · " + blackjackTotal(seat.cards());
        if (seat.folded()) info += " · " + tr(menu.kind.equals("poker") ? "casino.folded" : "casino.bust");
        if (seat.leaving()) info += " · " + tr("casino.leaving");
        text(graphics, shortText(info, width - 5), x + 3, y + 14, WHITE);
        if (showCards) cards(graphics, seat.cards(), x + 4, y + 25, 18, 25, width - 8, "seat" + seat.seat());
        if (!seat.result().isEmpty()) text(graphics, tr("casino.result." + seat.result()), x + width - 36, y + 42, GOLD);
    }

    private void emptySeat(GuiGraphics graphics, int index, int x, int y, int width) {
        graphics.fill(x, y, x + width, y + 55, 0xff193e31);
        center(graphics, tr("casino.seat", index + 1), x + width / 2, y + 23, 0xff759884);
    }

    private void cards(GuiGraphics graphics, List<Integer> cards, int x, int y, int width, int height, int available, String group) {
        int step = cards.size() < 2 ? width + 2 : Math.max(8, Math.min(width + 3, (available - width) / (cards.size() - 1)));
        for (int index = 0; index < cards.size(); index++) {
            CardMotion motion = cardMotions.get(group + ":" + index);
            double progress = motion == null || CasinoClientConfig.REDUCED.get() ? 1 : Math.max(0, Math.min(1, (clock() + menu.serverOffset - motion.started) / 350.0));
            int code = cards.get(index), drawX = x + index * step, drawY = y;
            if (motion != null && motion.previous == -2) drawY += (int) (12 * Math.pow(1 - progress, 3));
            boolean flipping = motion != null && motion.previous == -1 && code >= 0 && progress < 1;
            if (flipping) {
                graphics.pose().pushPose(); graphics.pose().translate(drawX + width / 2F, drawY, 0);
                graphics.pose().scale((float) Math.max(.08, Math.abs(progress * 2 - 1)), 1, 1);
                card(graphics, progress < .5 ? -1 : code, -width / 2, 0, width, height); graphics.pose().popPose();
            } else card(graphics, code, drawX, drawY, width, height);
        }
    }

    private void card(GuiGraphics graphics, int code, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, GOLD);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, code < 0 ? 0xff942b36 : WHITE);
        if (code < 0) {
            graphics.blit(CARD_BACK_TEXTURE, x, y, width, height, 0F, 0F, 32, 48, 32, 48);
            return;
        }
        int suit = code / 13, rank = code % 13 + 2;
        String label = rank == 14 ? "A" : rank == 13 ? "K" : rank == 12 ? "Q" : rank == 11 ? "J" : Integer.toString(rank);
        int color = suit == 1 || suit == 2 ? 0xffb32538 : 0xff192331;
        text(graphics, label, x + 2, y + 2, color);
        String[] pattern = switch (suit) {
            case 0 -> new String[]{"00100", "01110", "11111", "11111", "00100"};
            case 1 -> new String[]{"01010", "11111", "11111", "01110", "00100"};
            case 2 -> new String[]{"00100", "01110", "11111", "01110", "00100"};
            default -> new String[]{"00100", "01110", "10101", "11111", "00100"};
        };
        int pixel = height >= 32 ? 2 : 1;
        int baseX = x + (width - 5 * pixel) / 2, baseY = y + height - 5 * pixel - 3;
        for (int row = 0; row < 5; row++) for (int column = 0; column < 5; column++)
            if (pattern[row].charAt(column) == '1') graphics.fill(baseX + column * pixel, baseY + row * pixel,
                    baseX + (column + 1) * pixel, baseY + (row + 1) * pixel, color);
    }

    private void blackjack(GuiGraphics graphics, Game.View view) {
        center(graphics, tr("casino.dealer") + (view.dealer().isEmpty() ? "" : " · " + (view.dealer().contains(-1) ? "?" : blackjackTotal(view.dealer()))), 210, 48, WHITE);
        cards(graphics, view.dealer(), 178, 61, 24, 34, 130, "dealer");
        for (int index = 0; index < 6; index++) {
            int x = 9 + index % 3 * 136, y = 106 + index / 3 * 60;
            final int seatIndex = index;
            Game.Seat seat = view.seats().stream().filter(player -> player.seat() == seatIndex).findFirst().orElse(null);
            if (seat == null) emptySeat(graphics, index, x, y, 130); else seat(graphics, seat, x, y, 130, true);
        }
        String[] actions = {"bet", "hit", "stand", "double", "leave"};
        for (int index = 0; index < actions.length; index++) actionButton(graphics, 10 + index * 81, 230, 76, actions[index], view);
    }

    private int blackjackTotal(List<Integer> cards) {
        return de.shiina.casino.logic.Cards.blackjack(cards.stream().filter(code -> code >= 0)
                .map(code -> new de.shiina.casino.logic.Cards.Card(code % 13 + 2, code / 13)).toList());
    }

    private void poker(GuiGraphics graphics, Game.View view) {
        for (int index = 0; index < 6; index++) {
            int x = index < 3 ? 9 : 286, y = 52 + index % 3 * 57;
            final int seatIndex = index;
            Game.Seat seat = view.seats().stream().filter(player -> player.seat() == seatIndex).findFirst().orElse(null);
            if (seat == null) emptySeat(graphics, index, x, y, 125); else seat(graphics, seat, x, y, 125, true);
        }
        center(graphics, tr("casino.pot", view.pot()), 210, 77, GOLD);
        cards(graphics, view.board(), 142, 94, 24, 34, 137, "board");
        Game.Seat self = view.seats().stream().filter(player -> player.id().equals(ownId())).findFirst().orElse(null);
        center(graphics, tr("casino.yourcards"), 210, 151, WHITE);
        if (self != null) cards(graphics, self.cards(), 182, 164, 24, 34, 70, "seat" + self.seat());
        center(graphics, "Call: " + view.call(), 210, 136, WHITE);
        center(graphics, tr("casino.raisehint", view.minimumRaise()), 210, 208, GOLD);
        String[] actions = {"join", "fold", "check", "call", "raise", "allin", "leave"};
        for (int index = 0; index < actions.length; index++) actionButton(graphics, 8 + index * 58, 230, 55, actions[index], view);
    }

    private void roulette(GuiGraphics graphics, Game.View view) {
        drawWheel(graphics, 76, 112, 57, view, true);
        center(graphics, view.result() < 0 ? "?" : Integer.toString(view.result()), 76, 108, WHITE);
        boolean enabled = view.actions().contains("roulette") && amount() >= view.minBet() && amount() <= view.maxBet() && menu.chips >= amount();
        button(graphics, 148, 61, 19, 65, "0", "roulette_number", 0, enabled);
        for (int column = 0; column < 12; column++) for (int row = 0; row < 3; row++) {
            int number = column * 3 + 3 - row;
            button(graphics, 169 + column * 20, 61 + row * 22, 19, 21, Integer.toString(number), "roulette_number", number, enabled);
            graphics.fill(170 + column * 20, 62 + row * 22, 173 + column * 20, 65 + row * 22,
                    Roulette.red(number) ? 0xffd5434f : 0xff111111);
        }
        for (int index = 1; index <= 3; index++) {
            button(graphics, 149 + (index - 1) * 87, 131, 84, 19, tr("casino.dozen", index), "roulette_dozen", index, enabled);
            button(graphics, 149 + (index - 1) * 87, 175, 84, 18, tr("casino.column", index), "roulette_column", index, enabled);
        }
        String[] kinds = {"low", "even", "red", "black", "odd", "high"};
        int outsideX=139;
        int[] outsideWidths={42,46,32,54,44,46};
        for (int index = 0; index < kinds.length; index++) {
            button(graphics, outsideX, 153, outsideWidths[index], 19,tr("casino.roulette." + kinds[index]), "roulette_" + kinds[index], 0, enabled);
            outsideX+=outsideWidths[index]+1;
        }
        text(graphics, shortText(tr("casino.ownbets"), 320), 10, 198, GOLD);
        button(graphics, 338, 194, 70, 16, tr("casino.bets.list"), "bets", 0, true);
        int own = 0;
        for (int index = 0; index < view.bets().size(); index++) {
            Game.Bet bet = view.bets().get(index);
            if (!bet.owner().equals(ownId())) continue;
            String label = tr("casino.roulette." + bet.kind()) + (bet.kind().equals("number") || bet.kind().equals("dozen") || bet.kind().equals("column") ? " " + bet.number() : "") + ": " + bet.amount();
            if (own < 6) button(graphics, 10 + own % 3 * 136, 212 + own / 3 * 20, 132, 18, label, "cancel", index, view.actions().contains("cancel"));
            own++;
        }
        if (own == 0) text(graphics, tr("casino.none"), 10, 212, WHITE);
        button(graphics, 10, 174, 132, 19, tr("casino.last.wagers"), "repeat", 0, view.actions().contains("repeat") && repeatValid(view) && menu.chips >= repeatCost());
        if (view.phase().equals("result") && view.result() >= 0) {
            int n = view.result();
            int x = n == 0 ? 148 : 169 + (n - 1) / 3 * 20;
            int y = n == 0 ? 61 : 61 + (2 - (n - 1) % 3) * 22;
            outline(graphics, x, y, 19, n == 0 ? 65 : 21, GOLD);
        }
    }

    private void drawWheel(GuiGraphics graphics, int centerX, int centerY, int radius, Game.View view, boolean roulette) {
        int size = roulette ? 37 : view.segments().size();
        if (size < 2) return;
        int resultIndex = view.result();
        if (roulette && resultIndex >= 0) for (int index = 0; index < 37; index++) if (Roulette.ORDER[index] == view.result()) { resultIndex = index; break; }
        double sector = Math.PI * 2 / size;
        double target = -Math.PI / 2 - (Math.max(0, resultIndex) + .5) * sector;
        double fraction = view.phase().equals("spinning") ? Math.max(0, Math.min(1,
                (clock() + menu.serverOffset - view.startedMillis()) / (roulette ? 5000.0 : 4000.0))) : 1;
        double angle = view.result() < 0 ? 0 : CasinoClientConfig.REDUCED.get() ? target : target - Math.PI * 12 * Math.pow(1 - fraction, 3);
        final int winnerIndex=resultIndex;
        // All disk geometry precedes text. The provided GUI buffer is flushed once,
        // without allocating buffers or retaining a consumer across font calls.
        var vertices=graphics.bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.gui());
        var matrix=graphics.pose().last().pose();
        for (int vertical = -radius; vertical <= radius; vertical++) {
            int reach = (int) Math.sqrt(radius * radius - vertical * vertical);
            int spanStart = -reach, spanColor = 0;
            for (int horizontal = -reach; horizontal <= reach; horizontal++) {
                double direction = Math.atan2(vertical, horizontal) - angle;
                int segment = Math.floorMod((int) Math.floor(direction / sector), size);
                int color;
                if (roulette) { int number = Roulette.ORDER[segment]; color = number == 0 ? 0xff2b8754 : Roulette.red(number) ? 0xffae293e : 0xff1e2330; }
                else color = new int[]{0xffb93236,0xffe39921,0xff3ba046,0xff239a9f,0xff3266b1,0xff823da5}[segment % 6];
                if (view.phase().equals("result") && segment==winnerIndex && horizontal*horizontal+vertical*vertical>radius*radius*.7) color=GOLD;
                if (horizontal == -reach) spanColor = color;
                if (spanColor != color) {
                    span(vertices,matrix,centerX + spanStart,centerY + vertical,centerX + horizontal,centerY + vertical + 1,spanColor);
                    spanStart = horizontal; spanColor = color;
                }
            }
            span(vertices,matrix,centerX + spanStart,centerY + vertical,centerX + reach + 1,centerY + vertical + 1,spanColor);
        }
        graphics.flush();
        for (int segment = 0; segment < size; segment++) {
            double direction = angle + (segment + .5) * sector;
            int x = centerX + (int) (Math.cos(direction) * radius * .78), y = centerY + (int) (Math.sin(direction) * radius * .78);
            String label = roulette ? Integer.toString(Roulette.ORDER[segment]) : view.segments().get(segment) + "×";
            graphics.pose().pushPose(); graphics.pose().translate(x, y, 0);
            float labelScale = roulette ? .65F : 1F; graphics.pose().scale(labelScale, labelScale, 1);
            center(graphics, label, 0, -4, WHITE); graphics.pose().popPose();
        }
        graphics.fill(centerX - 14, centerY - 11, centerX + 15, centerY + 11, WOOD);
        for (int row = 0; row < 9; row++) graphics.fill(centerX - (9 - row) / 2, centerY - radius - 6 + row, centerX + (9 - row) / 2 + 1, centerY - radius - 5 + row, GOLD);
    }

    private void wheel(GuiGraphics graphics, Game.View view) {
        drawWheel(graphics, 83, 139, 68, view, false);
        text(graphics,tr("casino.amount.bet"),166,92,WHITE);
        button(graphics,166,108,150,20,amount+(editing?"_":""),"amount",0,true);
        button(graphics,324,108,34,20,"-","minus",0,true);
        button(graphics,366,108,34,20,"+","plus",0,true);
        button(graphics,166,142,112,20,tr("casino.last.bet"),"last",0,data("lastBet")>0);
        actionButton(graphics,286,142,114,"spin",view);
        if(view.phase().equals("result") && view.result()>=0)
            text(graphics,tr("casino.wheel.result",view.result()+1,view.segments().get(view.result())),166,179,GOLD);
        center(graphics,tr("casino.wheel.payouts"),WIDTH/2,230,GOLD);
        center(graphics,tr("casino.wheel.world"),WIDTH/2,250,WHITE);
    }
    private static void span(com.mojang.blaze3d.vertex.VertexConsumer vertices,org.joml.Matrix4f matrix,int x0,int y0,int x1,int y1,int color) {
        vertices.addVertex(matrix,x0,y0,0).setColor(color);vertices.addVertex(matrix,x0,y1,0).setColor(color);
        vertices.addVertex(matrix,x1,y1,0).setColor(color);vertices.addVertex(matrix,x1,y0,0).setColor(color);
    }

    private void exchange(GuiGraphics graphics) {
        center(graphics, tr("casino.exchange.rate", menu.rate), 210, 57, GOLD);
        text(graphics,tr("casino.exchange.available",menu.chips),16,80,WHITE);
        text(graphics,tr("casino.exchange.stored",menu.balance),16,97,WHITE);
        text(graphics,tr("casino.exchange.diamonds",data("diamonds")),16,114,WHITE);
        text(graphics,tr("casino.exchange.possible",data("maxBuy"),data("maxSell")),16,134,GOLD);
        text(graphics,tr("casino.exchange.buytitle"),16,156,WHITE);
        text(graphics,tr("casino.exchange.selltitle"),16,198,WHITE);
        for (int index=0;index<4;index++) {
            int buy=(int)(index==3?data("maxBuy"):new int[]{1,5,10}[index]);
            int sell=(int)(index==3?data("maxSell"):new int[]{1,5,10}[index]);
            button(graphics,16+index*99,170,94,20,index==3?tr("casino.exchange.max",buy):tr("casino.exchange.quantity",buy),"buy",buy,buy>0 && buy<=data("maxBuy"));
            button(graphics,16+index*99,212,94,20,index==3?tr("casino.exchange.max",sell):tr("casino.exchange.quantity",sell),"sell",sell,sell>0 && sell<=data("maxSell"));
        }
        button(graphics,48,246,324,22,tr("casino.exchange.claim",Math.min(menu.balance,data("claimCapacity"))),"claim",0,menu.balance>0 && data("claimCapacity")>0);
        center(graphics,tr("casino.exchange.safe"),210,284,GOLD);
    }

    private long repeatCost() {
        if (menu.lastState == null) return 0;
        long total = 0;
        for (var wager : menu.lastState.getAsJsonArray("lastWagers")) total += wager.getAsJsonObject().get("amount").getAsLong();
        return total;
    }
    private boolean repeatValid(Game.View view) {
        if (menu.lastState == null || menu.lastState.getAsJsonArray("lastWagers").isEmpty()) return false;
        for (var wager : menu.lastState.getAsJsonArray("lastWagers")) {
            long value = wager.getAsJsonObject().get("amount").getAsLong();
            if (value < view.minBet() || value > view.maxBet()) return false;
        }
        return true;
    }
    private String disabledReason(String action, int option) {
        if (pending && !local(action)) return tr("casino.status.pending");
        if (action.equals("last")) return tr("casino.error.template");
        if (menu.kind.equals("exchange")) {
            if (action.equals("claim")) return tr(menu.balance==0?"casino.error.balance":"casino.error.inventory");
            if (action.equals("buy")) return tr(data("diamonds")==0 || data("diamonds")<option?"casino.error.diamonds":"casino.error.inventory");
            if (action.equals("sell")) return tr(menu.chips<(long)Math.max(1,option)*menu.rate?"casino.error.chips":"casino.error.inventory");
        }
        Game.View view=menu.view;
        if(view==null) return tr("casino.loading");
        boolean wager=action.equals("bet") || action.equals("spin") || action.startsWith("roulette_");
        if ((wager || action.equals("join") || action.equals("repeat")) && menu.lastState!=null) {
            if (menu.lastState.has("near") && !menu.lastState.get("near").getAsBoolean()) return tr("casino.error.distance");
            if (menu.lastState.has("available") && !menu.lastState.get("available").getAsBoolean()) return tr("casino.error.busy");
        }
        if ((wager || action.equals("repeat") || action.equals("join")) && menu.lastState!=null && !menu.lastState.get("seated").getAsBoolean()
                && !action.equals("spin")) return tr("casino.error.sit");
        String permitted=action.startsWith("roulette_")?"roulette":action;
        if (!view.actions().contains(permitted)) {
            if ((wager || action.equals("repeat")) && !view.phase().equals("betting") && !action.equals("spin")) return tr("casino.error.betting_closed");
            if (view.seats().stream().anyMatch(Game.Seat::active) && !List.of("bet","join","leave").contains(action)) return tr("casino.error.turn");
            if (action.equals("repeat") && repeatCost()==0) return tr("casino.error.template");
            if (action.equals("spin")) return tr("casino.error.unavailable");
            if (action.equals("bet") || action.equals("join")) return tr(view.seats().stream().anyMatch(seat->seat.id().equals(ownId()))?"casino.error.already":"casino.error.phase");
            if (permitted.equals("roulette") || action.equals("repeat")) return tr("casino.error.wager_limit");
            return tr("casino.error.phase");
        }
        if (action.equals("repeat")) return tr(!repeatValid(view)?"casino.error.bet":menu.chips<repeatCost()?"casino.error.chips":"casino.error.wager_limit");
        if (wager || action.equals("join")) {
            long min=action.equals("join")?view.buyInMin():view.minBet(),max=action.equals("join")?view.buyInMax():view.maxBet();
            if (amount()<min || amount()>max) return tr("casino.error.bet");
            if (action.equals("bet") && amount()%2!=0) return tr("casino.error.even");
            if (menu.chips<amount()) return tr("casino.error.chips");
        }
        return tr(action.equals("double")?"casino.error.chips":action.equals("raise")?"casino.error.raise":"casino.error.phase");
    }
    private void outline(GuiGraphics graphics,int x,int y,int width,int height,int color) {
        graphics.fill(x,y,x+width,y+2,color); graphics.fill(x,y+height-2,x+width,y+height,color);
        graphics.fill(x,y,x+2,y+height,color); graphics.fill(x+width-2,y,x+width,y+height,color);
    }
    private String net(PersonalRounds.Result result) {
        return tr(result.net()>0?"casino.net.win":result.net()<0?"casino.net.loss":"casino.net.push",result.net()>0?"+"+result.net():Long.toString(result.net()));
    }
    private void personalResult(GuiGraphics graphics) {
        PersonalRounds.Result result=latestResult();
        if(result==null) return;
        int slide=CasinoClientConfig.REDUCED.get() || resultShownAt==0?0:(int)(5*Math.max(0,1-(clock()-resultShownAt)/300.0));
        graphics.fill(8,279+slide,WIDTH-8,HEIGHT-20,0xcc102f23);
        String summary=tr("casino.summary.last",tr("casino.summary",result.stake(),result.payout(),net(result)));
        var lines=font.split(Component.literal(summary),WIDTH-24);
        for(int i=0;i<Math.min(2,lines.size());i++) graphics.drawString(font,lines.get(i),12,282+slide+i*11,result.net()<0?0xffffb5a0:WHITE,false);
    }
    private void subview(GuiGraphics graphics) {
        center(graphics,tr("casino.page."+page),WIDTH/2,55,GOLD);
        if(page.equals("help")) {
            String help=tr("casino.help."+menu.kind,menu.rate,data("smallBlind"),data("bigBlind"));
            var lines=font.split(Component.literal(help),WIDTH-34);
            int visible=15; scroll=Math.max(0,Math.min(scroll,Math.max(0,lines.size()-visible)));
            for(int i=scroll;i<Math.min(lines.size(),scroll+visible);i++) graphics.drawString(font,lines.get(i),17,77+(i-scroll)*13,WHITE,false);
            if(lines.size()>visible) text(graphics,tr("casino.scroll"),280,HEIGHT-38,GOLD);
        } else if(page.equals("history")) {
            List<PersonalRounds.Result> history=history();
            if(history.isEmpty()) center(graphics,tr("casino.history.empty"),WIDTH/2,120,WHITE);
            text(graphics,tr("casino.history.game"),16,78,GOLD);
            text(graphics,tr("casino.history.stake"),126,78,GOLD);
            text(graphics,tr("casino.history.payout"),217,78,GOLD);
            text(graphics,tr("casino.history.net"),327,78,GOLD);
            for(int i=0;i<history.size();i++) {
                var result=history.get(i); int y=98+i*18;
                text(graphics,tr("casino.game."+result.game()),16,y,WHITE);
                text(graphics,Long.toString(result.stake()),126,y,WHITE);
                text(graphics,Long.toString(result.payout()),217,y,WHITE);
                text(graphics,result.net()>0?"+"+result.net():Long.toString(result.net()),327,y,result.net()<0?0xffffb5a0:WHITE);
            }
        } else if(page.equals("settings")) {
            button(graphics,40,85,340,24,tr("casino.settings.sounds",tr(CasinoClientConfig.SOUNDS.get()?"casino.on":"casino.off")),"sounds",0,true);
            center(graphics,tr("casino.settings.volume",Math.round(CasinoClientConfig.VOLUME.get()*100)),WIDTH/2,133,WHITE);
            button(graphics,120,154,70,22,"-","volumeDown",0,CasinoClientConfig.VOLUME.get()>0);
            button(graphics,230,154,70,22,"+","volumeUp",0,CasinoClientConfig.VOLUME.get()<1);
            button(graphics,40,205,340,24,tr("casino.settings.reduced",tr(CasinoClientConfig.REDUCED.get()?"casino.on":"casino.off")),"reduced",0,true);
        } else if(page.equals("bets") && menu.view!=null) {
            int own=0;
            for(int index=0;index<menu.view.bets().size();index++) {
                var bet=menu.view.bets().get(index); if(!bet.owner().equals(ownId())) continue;
                String label=tr("casino.roulette."+bet.kind())+(List.of("number","column","dozen").contains(bet.kind())?" "+bet.number():"")+": "+bet.amount();
                button(graphics,12+own%2*204,80+own/2*29,192,24,label,"cancel",index,menu.view.actions().contains("cancel")); own++;
            }
            if(own==0) center(graphics,tr("casino.none"),WIDTH/2,120,WHITE);
        }
        button(graphics,12,HEIGHT-43,105,22,tr("casino.back"),"back",0,true);
    }
    private void tooltip(GuiGraphics graphics) {
        String hint="";
        for(Control control:controls) if(mouseLocalX>=control.x && mouseLocalX<control.x+control.width && mouseLocalY>=control.y && mouseLocalY<control.y+control.height) {
            hint=control.enabled?control.text:control.text+"\n"+control.reason; break;
        }
        if(hint.isEmpty() && mouseLocalY>=HEIGHT-16 && !menu.error.isEmpty()) hint=tr(menu.error);
        if(hint.isEmpty()) return;
        var lines=font.split(Component.literal(hint),Math.min(290,WIDTH-24));
        int w=lines.stream().mapToInt(font::width).max().orElse(0)+10,h=lines.size()*11+8;
        int x=Math.max(5,Math.min(WIDTH-w-5,(int)mouseLocalX+8)),y=Math.max(22,Math.min(HEIGHT-h-5,(int)mouseLocalY+12));
        graphics.pose().pushPose();graphics.pose().translate(0,0,200);
        graphics.fill(x,y,x+w,y+h,0xf0101712);outline(graphics,x,y,w,h,GOLD);
        for(int i=0;i<lines.size();i++) graphics.drawString(font,lines.get(i),x+5,y+5+i*11,WHITE,false);
        graphics.pose().popPose();
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if(button!=0) return super.mouseClicked(mouseX,mouseY,button);
        double localX = (mouseX - originX) / scale, localY = (mouseY - originY) / scale;
        for (Control control : controls) {
            if (localX < control.x || localX >= control.x + control.width || localY < control.y || localY >= control.y + control.height || !control.enabled) continue;
            switch (control.action) {
                case "close" -> onClose();
                case "help", "history", "settings", "bets" -> { page=control.action;scroll=0;editing=false; }
                case "back" -> { page="";scroll=0; }
                case "last" -> { amount=Long.toString(data("lastBet"));editing=false;notice="casino.status.last";noticeUntil=clock()+3500; }
                case "sounds" -> { CasinoClientConfig.SOUNDS.set(!CasinoClientConfig.SOUNDS.get());CasinoClientConfig.SPEC.save(); }
                case "reduced" -> { CasinoClientConfig.REDUCED.set(!CasinoClientConfig.REDUCED.get());CasinoClientConfig.SPEC.save(); }
                case "volumeDown", "volumeUp" -> { CasinoClientConfig.VOLUME.set(Math.max(0,Math.min(1,Math.round((CasinoClientConfig.VOLUME.get()+(control.action.equals("volumeUp")?.1:-.1))*10)/10.0)));CasinoClientConfig.SPEC.save(); }
                case "amount" -> { editing = true; replaceAmount = true; }
                case "minus" -> amount = Long.toString(Math.max(0, amount() - 2));
                case "plus" -> amount = Long.toString(Math.min(amountMaximum(), amount() + 2));
                default -> {
                    editing = false; pending = true; sentAt = clock(); noticeUntil=0;
                    Game.View view = menu.view;
                    PacketDistributor.sendToServer(new CasinoNetwork.Action(menu.containerId, menu.table,
                            view == null ? 0 : view.round(), view == null ? menu.exchangeRevision : view.revision(), ++menu.sequence,
                            control.action, menu.kind.equals("exchange") ? control.option : amount(), menu.kind.equals("exchange") ? menu.rate : control.option));
                }
            }
            return true;
        }
        editing = false;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean charTyped(char character, int modifiers) {
        if (editing && character >= '0' && character <= '9') {
            if (replaceAmount) { amount = ""; replaceAmount = false; }
            if (amount.length() < 7) amount += character;
            return true;
        }
        return super.charTyped(character, modifiers);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if(key==GLFW.GLFW_KEY_ESCAPE && !page.isEmpty()) { page="";editing=false;return true; }
        if (editing) {
            if (key == GLFW.GLFW_KEY_BACKSPACE) { amount = amount.isEmpty() ? "" : amount.substring(0, amount.length() - 1); return true; }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_ESCAPE) { editing = false; return true; }
            if (key == GLFW.GLFW_KEY_A && hasControlDown()) { replaceAmount = true; return true; }
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }
    @Override public boolean mouseScrolled(double mouseX,double mouseY,double horizontal,double vertical) {
        if(page.equals("help")) { scroll=Math.max(0,scroll-(int)Math.signum(vertical)*3);return true; }
        return super.mouseScrolled(mouseX,mouseY,horizontal,vertical);
    }
}
