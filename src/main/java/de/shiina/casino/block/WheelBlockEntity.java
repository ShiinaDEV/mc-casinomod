package de.shiina.casino.block;

import de.shiina.casino.CasinoContent;
import de.shiina.casino.logic.WheelAnimation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** No ticker, inventory, RNG or payout logic. Vanilla chunk tracking synchronizes this small state. */
public final class WheelBlockEntity extends BlockEntity {
    public static java.util.function.Consumer<WheelBlockEntity> clientAnimation=wheel -> {};
    public long serial,round,startedTick;
    public int duration,result=-1;
    public boolean spinning;
    public WheelBlockEntity(BlockPos pos,BlockState state) { super(CasinoContent.WORLD_WHEEL.get(),pos,state); }
    public void animation(long round,boolean spinning,long start,int duration,int result) {
        if(this.round==round && this.spinning==spinning && this.result==result && (!spinning || startedTick==start)) return;
        this.serial++; this.round=round; this.spinning=spinning; this.startedTick=start; this.duration=duration; this.result=result;
        setChanged(); if(level!=null) level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.saveAdditional(tag,registries); tag.putLong("serial",serial); tag.putLong("round",round);
        tag.putLong("start",startedTick); tag.putInt("duration",duration); tag.putInt("result",result); tag.putBoolean("spinning",spinning);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.loadAdditional(tag,registries);
        long incoming=tag.getLong("serial");
        if(level!=null && level.isClientSide && !WheelAnimation.newer(incoming,serial) && serial!=0) return;
        serial=incoming; round=tag.getLong("round"); startedTick=tag.getLong("start"); duration=tag.getInt("duration");
        result=tag.contains("result")?tag.getInt("result"):-1;
        // A restart recovers ledger obligations; it never resumes an already decided spin.
        spinning=tag.getBoolean("spinning");
        if(level!=null && level.isClientSide) clientAnimation.accept(this);
    }
    @Override public void onLoad() {
        super.onLoad();
        if(level!=null && !level.isClientSide && spinning) { spinning=false; serial++; setChanged(); }
        if(level!=null && level.isClientSide) clientAnimation.accept(this);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
