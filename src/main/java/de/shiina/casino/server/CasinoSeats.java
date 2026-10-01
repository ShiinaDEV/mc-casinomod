package de.shiina.casino.server;

import de.shiina.casino.CasinoContent;
import de.shiina.casino.block.CasinoBlock;
import de.shiina.casino.block.StationPartBlock;
import de.shiina.casino.block.StationStructure;
import de.shiina.casino.entity.CasinoSeatEntity;
import de.shiina.casino.logic.SeatRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

public final class CasinoSeats {
    private static final Map<MinecraftServer,CasinoSeats> INSTANCES=new HashMap<>();
    private final MinecraftServer server;
    public final SeatRegistry registry=new SeatRegistry();
    private final Map<UUID,CasinoSeatEntity> mounts=new HashMap<>();
    private CasinoSeats(MinecraftServer server) { this.server=server; }
    public static CasinoSeats get(MinecraftServer server) { return INSTANCES.computeIfAbsent(server,CasinoSeats::new); }
    public boolean at(ServerPlayer player,BlockPos anchor) {
        var key=registry.seat(player.getUUID());
        return key!=null && key.dimension().equals(player.level().dimension().location().toString()) && key.station()==anchor.asLong()
                && player.getVehicle() instanceof CasinoSeatEntity seat && seat==mounts.get(player.getUUID());
    }
    public int index(UUID player,String dimension,BlockPos anchor) {
        var key=registry.seat(player);
        return key!=null && key.dimension().equals(dimension) && key.station()==anchor.asLong() ? key.index() : -1;
    }
    public boolean occupied(String dimension,BlockPos anchor) { return !registry.at(dimension,anchor.asLong()).isEmpty(); }
    public void sit(ServerPlayer player,BlockPos anchor,BlockPos chair,int index,String kind) {
        var state=player.level().getBlockState(chair);
        if (!player.isAlive() || player.isSpectator() || !player.canInteractWithBlock(chair,0)
                || !(state.getBlock() instanceof StationPartBlock part) || !part.kind.equals(kind) || part.seat(state)!=index
                || !anchor.equals(StationStructure.anchor(player.level(),chair))) return;
        if (at(player,anchor) && registry.seat(player.getUUID()).index()==index) { CasinoServer.get(server).open(player,anchor,kind); return; }
        if (player.isPassenger() || !CasinoServer.get(server).canSit(player,anchor,index)) { error(player); return; }
        var key=new SeatRegistry.Key(player.level().dimension().location().toString(),anchor.asLong(),index);
        if (!registry.claim(player.getUUID(),key)) { error(player); return; }
        CasinoSeatEntity mount=CasinoContent.SEAT.get().create(player.level());
        if (mount==null) { registry.release(player.getUUID()); return; }
        mount.station=anchor.immutable(); mount.chair=chair.immutable(); mount.owner=player.getUUID();
        Direction direction=index<3?Direction.SOUTH:Direction.NORTH;
        for(int i=0;i<StationStructure.turns(state.getValue(CasinoBlock.FACING));i++) direction=direction.getClockWise();
        mount.moveTo(chair.getX()+.5,chair.getY()+.625,chair.getZ()+.5,direction.toYRot(),0);
        mounts.put(player.getUUID(),mount);
        if (!player.serverLevel().addFreshEntity(mount) || !player.startRiding(mount,true)) { release(player.getUUID()); return; }
        var marker=new net.minecraft.nbt.CompoundTag(); marker.putLong("chair",chair.asLong()); marker.putFloat("yaw",direction.toYRot());
        marker.putString("dimension",key.dimension()); player.getPersistentData().put("shiinacasinoSeat",marker);
        player.setYRot(direction.toYRot()); player.setYHeadRot(direction.toYRot()); player.setXRot(0);
        CasinoServer.get(server).open(player,anchor,kind);
    }
    private static void error(ServerPlayer player) { player.displayClientMessage(Component.translatable("casino.error.seat"),true); }
    public void release(UUID player) {
        var key=registry.release(player);
        CasinoSeatEntity mount=mounts.remove(player);
        ServerPlayer online=server.getPlayerList().getPlayer(player);
        if(online!=null) online.getPersistentData().remove("shiinacasinoSeat");
        if (mount!=null) { mount.ejectPassengers(); mount.discard(); }
        if (key!=null) CasinoServer.get(server).seatReleased(player,key.dimension(),BlockPos.of(key.station()));
    }
    public void releaseStation(String dimension,BlockPos anchor) { for(UUID player:registry.at(dimension,anchor.asLong())) release(player); }
    public void releaseAll() { for(UUID player:List.copyOf(mounts.keySet())) release(player); registry.clear(); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if(event.getEntity() instanceof ServerPlayer p) get(p.server).release(p.getUUID()); }
    @SubscribeEvent public static void death(LivingDeathEvent event) { if(event.getEntity() instanceof ServerPlayer p) get(p.server).release(p.getUUID()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if(event.getEntity() instanceof ServerPlayer p) get(p.server).release(p.getUUID()); }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if(event.getEntity() instanceof ServerPlayer p) {
            var marker=p.getPersistentData().getCompound("shiinacasinoSeat");
            get(p.server).release(p.getUUID()); if(p.getVehicle() instanceof CasinoSeatEntity) p.stopRiding();
            if(marker.contains("chair") && marker.getString("dimension").equals(p.level().dimension().location().toString())) {
                BlockPos chair=BlockPos.of(marker.getLong("chair"));
                if(p.level().hasChunkAt(chair) && p.distanceToSqr(chair.getCenter())<=16) {
                    var target=CasinoSeatEntity.safeDismount(p.level(),p,new net.minecraft.world.phys.Vec3(chair.getX()+.5,chair.getY()+.625,chair.getZ()+.5),marker.getFloat("yaw"),chair);
                    p.teleportTo(target.x,target.y,target.z);
                }
            }
            p.getPersistentData().remove("shiinacasinoSeat");
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { INSTANCES.remove(event.getServer()); }
    @SubscribeEvent public static void removed(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) {
        if(!event.getLevel().isClientSide && event.getEntity() instanceof CasinoSeatEntity seat && seat.owner!=null)
            get(event.getLevel().getServer()).release(seat.owner);
    }
}
