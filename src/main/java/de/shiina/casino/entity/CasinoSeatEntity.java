package de.shiina.casino.entity;

import de.shiina.casino.block.StationPartBlock;
import de.shiina.casino.block.StationStructure;
import de.shiina.casino.server.CasinoSeats;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Invisible mount, created only while occupied. It has no inventory and is never saved. */
public final class CasinoSeatEntity extends Entity {
    public BlockPos station=BlockPos.ZERO, chair=BlockPos.ZERO;
    public UUID owner;
    public CasinoSeatEntity(EntityType<? extends CasinoSeatEntity> type,Level level) {
        super(type,level); noPhysics=true; setNoGravity(true); setInvulnerable(true);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) { if (!level().isClientSide) discard(); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public boolean shouldBeSaved() { return false; }
    @Override protected boolean canAddPassenger(Entity passenger) { return passenger instanceof Player && getPassengers().isEmpty() && (level().isClientSide || passenger.getUUID().equals(owner)); }
    @Override protected Vec3 getPassengerAttachmentPoint(Entity passenger,EntityDimensions dimensions,float partialTick) { return Vec3.ZERO; }
    @Override protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!level().isClientSide && level().getServer()!=null) CasinoSeats.get(level().getServer()).release(passenger.getUUID());
    }
    @Override public void tick() {
        super.tick();
        if (!level().isClientSide) {
            BlockPos anchor=level().hasChunkAt(chair)?StationStructure.anchor(level(),chair):null;
            if (!level().hasChunkAt(chair) || getPassengers().size()!=1 || !getFirstPassenger().isAlive() || getFirstPassenger().isSpectator() || getFirstPassenger().level()!=level()
                    || !(level().getBlockState(chair).getBlock() instanceof StationPartBlock) || !station.equals(anchor)) {
                if (owner!=null) CasinoSeats.get(level().getServer()).release(owner);
                discard();
            }
        }
    }
    @Override public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return safeDismount(level(),passenger,position(),getYRot(),chair);
    }
    public static Vec3 safeDismount(Level level,LivingEntity passenger,Vec3 position,float yaw,BlockPos chair) {
        // Prefer behind the chair, then search a small, bounded area with headroom and solid ground.
        Vec3 backward=Vec3.directionFromRotation(0,yaw).scale(-1);
        BlockPos preferred=BlockPos.containing(position.x+backward.x,position.y,position.z+backward.z);
        for(int radius=0;radius<=3;radius++) for(int dx=-radius;dx<=radius;dx++) for(int dz=-radius;dz<=radius;dz++) {
            if (Math.max(Math.abs(dx),Math.abs(dz))!=radius) continue;
            for(int dy=0;dy<=1;dy++) {
                BlockPos feet=preferred.offset(dx,dy,dz);
                if (!level.hasChunkAt(feet) || !level.getWorldBorder().isWithinBounds(feet)) continue;
                var floor=level.getBlockState(feet.below());
                if (!floor.isFaceSturdy(level,feet.below(),net.minecraft.core.Direction.UP) || floor.is(Blocks.MAGMA_BLOCK)
                        || floor.is(Blocks.CACTUS) || !level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.below()).isEmpty()
                        || level.getBlockState(feet).is(Blocks.FIRE) || level.getBlockState(feet).is(Blocks.SOUL_FIRE)) continue;
                Vec3 target=new Vec3(feet.getX()+.5,feet.getY(),feet.getZ()+.5);
                AABB standing=passenger.getDimensions(net.minecraft.world.entity.Pose.STANDING).makeBoundingBox(target);
                if (level.noCollision(passenger,standing)) return target;
            }
        }
        // No ground nearby: above the chair avoids trapping a standing player in its collision.
        return new Vec3(position.x,chair.getY()+1.4,position.z);
    }
}
