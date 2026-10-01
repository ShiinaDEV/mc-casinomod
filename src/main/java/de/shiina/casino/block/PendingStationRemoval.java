package de.shiina.casino.block;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** Exact owned cells awaiting normal chunk loading. Separate from games and chip accounts. */
public final class PendingStationRemoval extends SavedData {
    private record Removal(BlockState state,boolean drop) {}
    private final Map<Long,Map<BlockPos,Removal>> chunks=new HashMap<>();
    public static PendingStationRemoval get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(PendingStationRemoval::new,PendingStationRemoval::load,null),"shiinacasino_structure_cleanup");
    }
    public void remember(BlockPos pos,BlockState state) {
        chunks.computeIfAbsent(ChunkPos.asLong(pos),k->new HashMap<>()).put(pos.immutable(),new Removal(state,false));setDirty();
    }
    public void rememberOwner(BlockPos pos,BlockState state,boolean drop) {
        // playerWillDestroy records Creative before the generic onRemove callback.
        chunks.computeIfAbsent(ChunkPos.asLong(pos),k->new HashMap<>()).putIfAbsent(pos.immutable(),new Removal(state,drop));setDirty();
    }
    public void clean(ServerLevel level,ChunkPos chunk) {
        if(!level.hasChunk(chunk.x,chunk.z)) return;
        var cells=chunks.remove(chunk.toLong());
        if(cells==null) return;
        for(var cell:cells.entrySet()) if(level.getBlockState(cell.getKey()).equals(cell.getValue().state())) {
            if(cell.getValue().state().getBlock() instanceof CasinoBlock) level.destroyBlock(cell.getKey(),cell.getValue().drop());
            else level.removeBlock(cell.getKey(),false);
        }
        setDirty();
    }
    public static PendingStationRemoval load(CompoundTag tag,HolderLookup.Provider registries) {
        var data=new PendingStationRemoval();var list=tag.getList("parts",10);
        for(int i=0;i<list.size();i++) {
            var entry=list.getCompound(i);var state=NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK),entry.getCompound("state"));
            if(state.getBlock() instanceof StationPartBlock) data.remember(BlockPos.of(entry.getLong("pos")),state);
            else if(state.getBlock() instanceof CasinoBlock && state.getValue(CasinoBlock.EXPANDED)) data.rememberOwner(BlockPos.of(entry.getLong("pos")),state,entry.getBoolean("drop"));
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
        var list=new ListTag();
        for(var cells:chunks.values()) for(var cell:cells.entrySet()) {
            var entry=new CompoundTag();entry.putLong("pos",cell.getKey().asLong());entry.put("state",NbtUtils.writeBlockState(cell.getValue().state()));entry.putBoolean("drop",cell.getValue().drop());list.add(entry);
        }
        tag.put("parts",list);return tag;
    }
    @SubscribeEvent public static void loaded(ChunkEvent.Load event) {
        if(event.getLevel() instanceof ServerLevel level) {
            ChunkPos pos=event.getChunk().getPos();
            // Chunk loading may notify off-thread; mutation stays on the server thread.
            level.getServer().execute(()->get(level).clean(level,pos));
        }
    }
}
