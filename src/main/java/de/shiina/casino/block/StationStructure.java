package de.shiina.casino.block;

import de.shiina.casino.CasinoContent;
import de.shiina.casino.logic.StationLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class StationStructure {
    // Only populated during a synchronous server placement transaction.
    private static final java.util.Map<Level,java.util.Set<BlockPos>> BUILDING=new java.util.IdentityHashMap<>();
    public static boolean isBuilding(Level level,BlockPos anchor) {
        var anchors=BUILDING.get(level);return anchors!=null && anchors.contains(anchor);
    }
    public static int turns(Direction facing) { return switch (facing) { case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> 0; }; }
    public static BlockPos partPos(BlockPos anchor, StationLayout.Part part, Direction facing) {
        var rotated = part.rotated(turns(facing)); return anchor.offset(rotated.x(), rotated.y(), rotated.z());
    }
    public static BlockState partState(String kind, int index, Direction facing) {
        return CasinoContent.PART_BLOCKS.get(kind).get().defaultBlockState().setValue(StationPartBlock.PART, index).setValue(CasinoBlock.FACING, facing);
    }
    public static BlockPos anchor(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof StationPartBlock part)) return pos;
        if (state.getValue(StationPartBlock.PART) >= StationLayout.parts(part.kind).size()) return null;
        BlockPos anchor = ownerFromState(pos,state,part.kind);
        if (!level.hasChunkAt(anchor)) return null;
        BlockState owner = level.getBlockState(anchor);
        return owner.getBlock() instanceof CasinoBlock block && block.kind.equals(part.kind)
                && owner.getValue(CasinoBlock.EXPANDED) && owner.getValue(CasinoBlock.FACING) == state.getValue(CasinoBlock.FACING) ? anchor : null;
    }
    public static BlockPos ownerFromState(BlockPos pos, BlockState state, String kind) {
        int index=state.getValue(StationPartBlock.PART);
        if (index >= StationLayout.parts(kind).size()) return pos;
        var offset=StationLayout.parts(kind).get(index).rotated(turns(state.getValue(CasinoBlock.FACING)));
        return pos.offset(-offset.x(),-offset.y(),-offset.z());
    }
    public record Cell(BlockPos pos, BlockState state) {}
    private static java.util.List<Cell> cells(BlockPos anchor, BlockState state) {
        CasinoBlock block=(CasinoBlock)state.getBlock();
        var result=new java.util.ArrayList<Cell>();result.add(new Cell(anchor,state));
        if(state.getValue(CasinoBlock.EXPANDED)) {
            var layout=StationLayout.parts(block.kind);
            for(int i=0;i<layout.size();i++) result.add(new Cell(partPos(anchor,layout.get(i),state.getValue(CasinoBlock.FACING)),partState(block.kind,i,state.getValue(CasinoBlock.FACING))));
        }
        return result;
    }
    public static net.minecraft.network.chat.Component failure(net.minecraft.world.item.context.BlockPlaceContext context, BlockState state) {
        return failure(context.getLevel(),context.getClickedPos(),state,context.getPlayer(),context,false);
    }
    private static net.minecraft.network.chat.Component reason(String key,BlockPos pos) {
        return net.minecraft.network.chat.Component.translatable("casino.placement."+key,pos.toShortString());
    }
    private static boolean replaceable(Level level, BlockPos pos, net.minecraft.world.item.context.BlockPlaceContext context) {
        BlockState old=level.getBlockState(pos);
        if(context==null) return old.canBeReplaced();
        // The hit is this cell itself, never the adjacent cell selected by the anchor context.
        var local=new net.minecraft.world.item.context.BlockPlaceContext(level,context.getPlayer(),context.getHand(),context.getItemInHand(),
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),Direction.UP,pos,false));
        return old.canBeReplaced(local);
    }
    private static net.minecraft.network.chat.Component failure(Level level, BlockPos anchor, BlockState state, Player player,
            net.minecraft.world.item.context.BlockPlaceContext context, boolean installedAnchor) {
        var block=(CasinoBlock)state.getBlock();
        for(var cell:cells(anchor,state)) {
            BlockPos pos=cell.pos();
            if(!level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos)) return reason("bounds",pos);
            // Loaded neighbours are supported; never force-load chunks or add tickets.
            if(!level.hasChunkAt(pos)) return reason("unloaded",pos);
            if(player!=null && (!player.mayBuild() || !level.mayInteract(player,pos)
                    || context!=null && !player.mayUseItemAt(pos,context.getClickedFace(),context.getItemInHand()))) return reason("permission",pos);
            if(!(installedAnchor && pos.equals(anchor) && level.getBlockState(pos).equals(state)) && !replaceable(level,pos,context)) return reason("blocked",pos);
            if(pos.getY()==anchor.getY()) {
                if(!level.hasChunkAt(pos.below()) || !level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP)) return reason("floor",pos.below());
            }
            int index=cell.state().getBlock() instanceof StationPartBlock ? cell.state().getValue(StationPartBlock.PART) : -1;
            if(index>=0 && StationLayout.parts(block.kind).get(index).seat()>=0) {
                for(int height=1;height<=2;height++) {
                    BlockPos head=pos.above(height);
                    if(!level.isInWorldBounds(head) || !level.getWorldBorder().isWithinBounds(head)) return reason("bounds",head);
                    if(!replaceable(level,head,context)) return reason("headroom",head);
                }
            }
            // Check the final expanded shape. World blocks are checked separately above;
            // neither the existing floor nor an installed anchor is an entity obstruction.
            if(!level.isUnobstructed(cell.state(),pos,player==null ? CollisionContext.empty() : CollisionContext.of(player))) return reason("entity",pos);
            if(!cell.state().canSurvive(level,pos)) return reason("floor",pos.below());
        }
        return null;
    }
    public static boolean space(Level level, BlockPos anchor, BlockState state, Player player) {
        return failure(level,anchor,state,player,null,false)==null;
    }
    public static boolean place(net.minecraft.world.item.context.BlockPlaceContext context, BlockState state) {
        if(failure(context,state)!=null) return false;
        return write(context.getLevel(),cells(context.getClickedPos(),state),context.getClickedPos(),state);
    }
    /** For command/test callers with an existing anchor; items use place before any writes. */
    public static boolean install(Level level, BlockPos anchor, BlockState state) {
        if(complete(level,anchor,state)) return true;
        if(failure(level,anchor,state,null,null,true)!=null) return false;
        var parts=new java.util.ArrayList<>(cells(anchor,state));parts.removeFirst();
        return write(level,parts,anchor,state);
    }
    private static boolean write(Level level, java.util.List<Cell> cells, BlockPos anchor, BlockState state) {
        var previous=new java.util.LinkedHashMap<BlockPos,BlockState>();
        for(var cell:cells) previous.put(cell.pos(),level.getBlockState(cell.pos()));
        BUILDING.computeIfAbsent(level,k->new java.util.HashSet<>()).add(anchor);
        boolean success=false;
        try {
            // No neighbour callbacks or packets until the whole structure exists.
            for(var cell:cells) if(!level.setBlock(cell.pos(),cell.state(),Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS)) return false;
            if(!level.getBlockState(anchor).equals(state) || !complete(level,anchor,state)) return false;
            for(var cell:cells) {
                level.sendBlockUpdated(cell.pos(),previous.get(cell.pos()),cell.state(),Block.UPDATE_ALL);
                level.updateNeighborsAt(cell.pos(),cell.state().getBlock());
                cell.state().updateNeighbourShapes(level,cell.pos(),Block.UPDATE_ALL);
            }
            success=level.getBlockState(anchor).equals(state) && complete(level,anchor,state);
            return success;
        } finally {
            try {
                if(!success) {
                    // Remove the owner FIRST: part callbacks must never drop an item on rollback.
                    if(level.getBlockState(anchor).equals(state)) level.removeBlock(anchor,false);
                    for(var entry:previous.entrySet()) level.setBlock(entry.getKey(),entry.getValue(),Block.UPDATE_ALL);
                }
            } finally {
                var anchors=BUILDING.get(level);anchors.remove(anchor);
                if(anchors.isEmpty()) BUILDING.remove(level);
            }
        }
    }
    public static boolean complete(Level level, BlockPos anchor, BlockState state) {
        CasinoBlock block = (CasinoBlock) state.getBlock(); var layout = state.getValue(CasinoBlock.EXPANDED) ? StationLayout.parts(block.kind) : java.util.List.<StationLayout.Part>of();
        for (int i = 0; i < layout.size(); i++) {
            BlockPos pos = partPos(anchor,layout.get(i),state.getValue(CasinoBlock.FACING));
            if (!level.hasChunkAt(pos) || !level.getBlockState(pos).equals(partState(block.kind,i,state.getValue(CasinoBlock.FACING)))) return false;
        }
        return true;
    }
    public static void removeParts(Level level, BlockPos anchor, BlockState state) {
        CasinoBlock block = (CasinoBlock) state.getBlock(); var layout = state.getValue(CasinoBlock.EXPANDED) ? StationLayout.parts(block.kind) : java.util.List.<StationLayout.Part>of();
        for (int i = 0; i < layout.size(); i++) {
            BlockPos pos = partPos(anchor,layout.get(i),state.getValue(CasinoBlock.FACING));
            BlockState expected=partState(block.kind,i,state.getValue(CasinoBlock.FACING));
            if(level.hasChunkAt(pos)) {
                if(level.getBlockState(pos).equals(expected)) level.removeBlock(pos,false);
            } else if(level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                PendingStationRemoval.get(serverLevel).remember(pos,expected);
            }
        }
    }
    private StationStructure() {}
}
