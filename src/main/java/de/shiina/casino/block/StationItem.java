package de.shiina.casino.block;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

/** Vanilla item consumption, sound, advancement and BE data follow a successful transaction. */
public final class StationItem extends BlockItem {
    public StationItem(CasinoBlock block, Properties properties) { super(block, properties); }
    private BlockState requestedState(BlockPlaceContext context) {
        BlockState state=getBlock().getStateForPlacement(context);
        if(state==null) return null;
        state=context.getItemInHand().getOrDefault(DataComponents.BLOCK_STATE,BlockItemStateProperties.EMPTY).apply(state);
        return state.setValue(CasinoBlock.EXPANDED,de.shiina.casino.logic.StationLayout.enlarged(((CasinoBlock)getBlock()).kind));
    }
    @Override protected BlockState getPlacementState(BlockPlaceContext context) {
        BlockState state=requestedState(context);
        return state!=null && canPlace(context,state) ? state : null;
    }
    @Override protected boolean canPlace(BlockPlaceContext context, BlockState state) {
        return StationStructure.failure(context,state)==null;
    }
    @Override protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        return StationStructure.place(context,state);
    }
    @Override public InteractionResult place(BlockPlaceContext context) {
        BlockState state=requestedState(context);
        var failure=state==null ? Component.translatable("casino.placement.state") : StationStructure.failure(context,state);
        if(failure!=null || !context.canPlace()) {
            tell(context,failure!=null ? failure : Component.translatable("casino.placement.blocked",context.getClickedPos().toShortString()));
            return InteractionResult.FAIL;
        }
        // Predict the interaction only. All cells and the item count come from the server,
        // so rejected placement cannot leave client-only parts behind.
        if(context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        var original=context.getItemInHand().get(DataComponents.BLOCK_STATE);
        // Vanilla reapplies this component AFTER placeBlock: keep the checked orientation
        // and footprint, even for items carrying legacy expanded=false properties.
        context.getItemInHand().set(DataComponents.BLOCK_STATE,BlockItemStateProperties.EMPTY.with(CasinoBlock.FACING,state.getValue(CasinoBlock.FACING)).with(CasinoBlock.EXPANDED,state.getValue(CasinoBlock.EXPANDED)));
        try {
            InteractionResult result=super.place(context);
            if(!result.consumesAction()) tell(context,Component.translatable("casino.placement.changed"));
            return result;
        } finally {
            if(original==null) context.getItemInHand().remove(DataComponents.BLOCK_STATE);
            else context.getItemInHand().set(DataComponents.BLOCK_STATE,original);
        }
    }
    private static void tell(BlockPlaceContext context, Component reason) {
        if(!context.getLevel().isClientSide && context.getPlayer()!=null)
            context.getPlayer().displayClientMessage(Component.translatable("casino.placement.failed",reason),true);
    }
}
