package de.shiina.casino.block;

import de.shiina.casino.server.CasinoServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class CasinoBlock extends HorizontalDirectionalBlock implements net.minecraft.world.level.block.EntityBlock {
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty EXPANDED = net.minecraft.world.level.block.state.properties.BooleanProperty.create("expanded");
    public final String kind;
    private static final VoxelShape TABLE = Shapes.or(Block.box(0, 11, 0, 16, 15, 16),
            Block.box(1, 0, 1, 4, 11, 4), Block.box(12, 0, 1, 15, 11, 4),
            Block.box(1, 0, 12, 4, 11, 15), Block.box(12, 0, 12, 15, 11, 15));

    public CasinoBlock(String kind, Properties properties) {
        super(properties); this.kind = kind; registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(EXPANDED,false));
    }
    @Override protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(properties -> new CasinoBlock(kind, properties));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING,EXPANDED); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); }
    @Override protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(EXPANDED) && de.shiina.casino.logic.StationLayout.enlarged(kind)) return StationShapes.part(kind,-1,state.getValue(FACING));
        return kind.equals("exchange") ? Shapes.block() : kind.equals("wheel") ? Block.box(3,0,3,13,16,13) : TABLE;
    }
    @Override public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos,BlockState state) {
        return kind.equals("wheel") || kind.equals("roulette") ? new de.shiina.casino.block.WheelBlockEntity(pos,state) : null;
    }
    @Override protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        return stack.getItem() instanceof StationItem ? net.minecraft.world.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION
                : net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) CasinoServer.get(serverPlayer.server).open(serverPlayer, pos, kind);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!level.isClientSide && state.getBlock() != next.getBlock()) {
            de.shiina.casino.server.CasinoSeats.get(level.getServer()).releaseStation(level.dimension().location().toString(),pos);
            CasinoServer.get(level.getServer()).remove(level.dimension().location().toString(), pos);
            if (state.getValue(EXPANDED)) StationStructure.removeParts(level,pos,state);
        }
        super.onRemove(state, level, pos, next, moving);
    }
}
