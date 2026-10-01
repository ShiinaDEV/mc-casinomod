package de.shiina.casino.block;

import de.shiina.casino.logic.StationLayout;
import de.shiina.casino.server.CasinoSeats;
import de.shiina.casino.server.CasinoServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class StationPartBlock extends HorizontalDirectionalBlock {
    public static final IntegerProperty PART = IntegerProperty.create("part",0,13);
    public final String kind;
    public StationPartBlock(String kind, Properties properties) {
        super(properties); this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING,net.minecraft.core.Direction.NORTH).setValue(PART,0));
    }
    @Override protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() { return simpleCodec(p -> new StationPartBlock(kind,p)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(FACING,PART); }
    public int seat(BlockState state) { int index=state.getValue(PART); return index < StationLayout.parts(kind).size() ? StationLayout.parts(kind).get(index).seat() : -1; }
    @Override protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return StationShapes.part(kind,state.getValue(PART),state.getValue(FACING));
    }
    @Override protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        return stack.getItem() instanceof StationItem ? net.minecraft.world.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION
                : net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            BlockPos anchor=StationStructure.anchor(level,pos);
            if (anchor != null) {
                if (seat(state) >= 0) CasinoSeats.get(serverPlayer.server).sit(serverPlayer,anchor,pos,seat(state),kind);
                else CasinoServer.get(serverPlayer.server).open(serverPlayer,anchor,kind);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockPos anchor=StationStructure.anchor(level,pos);
            if (anchor != null) {
                if(StationStructure.isBuilding(level,anchor)) level.removeBlock(anchor,false);
                else level.destroyBlock(anchor,!player.isCreative(),player);
            }
            else if(state.getValue(PART)<StationLayout.parts(kind).size() && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                BlockPos owner=StationStructure.ownerFromState(pos,state,kind);
                if(!level.hasChunkAt(owner)) queueOwner(serverLevel,owner,state,!player.isCreative());
            }
        }
        return super.playerWillDestroy(level,pos,state,player);
    }
    private void queueOwner(net.minecraft.server.level.ServerLevel level,BlockPos owner,BlockState part,boolean drop) {
        BlockState expected=de.shiina.casino.CasinoContent.TABLE_BLOCKS.get(kind).get().defaultBlockState()
                .setValue(CasinoBlock.EXPANDED,true).setValue(FACING,part.getValue(FACING));
        PendingStationRemoval.get(level).rememberOwner(owner,expected,drop);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!level.isClientSide && state.getBlock()!=next.getBlock()) {
            BlockPos anchor=StationStructure.ownerFromState(pos,state,kind);
            if (state.getValue(PART)<StationLayout.parts(kind).size() && level.hasChunkAt(anchor)
                    && level.getBlockState(anchor).getBlock() instanceof CasinoBlock block && block.kind.equals(kind)
                    && level.getBlockState(anchor).getValue(CasinoBlock.EXPANDED)
                    && level.getBlockState(anchor).getValue(FACING)==state.getValue(FACING)) {
                if(StationStructure.isBuilding(level,anchor)) level.removeBlock(anchor,false);
                else level.destroyBlock(anchor,true);
            }
            else if(state.getValue(PART)<StationLayout.parts(kind).size() && !level.hasChunkAt(anchor)
                    && level instanceof net.minecraft.server.level.ServerLevel serverLevel) queueOwner(serverLevel,anchor,state,true);
        }
        super.onRemove(state,level,pos,next,moving);
    }
}
