package de.shiina.casino;

import com.mojang.authlib.GameProfile;
import de.shiina.casino.block.*;
import de.shiina.casino.logic.StationLayout;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@net.neoforged.fml.common.EventBusSubscriber(modid=ShiinaCasino.ID,bus=net.neoforged.fml.common.EventBusSubscriber.Bus.MOD)
@GameTestHolder(ShiinaCasino.ID)
@PrefixGameTestTemplate(false)
public final class StationPlacementGameTests {
    private static final net.minecraft.resources.ResourceLocation ROLLBACK=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ShiinaCasino.ID,"placement_rollback_probe");
    private static final class CallbackFloor extends net.minecraft.world.level.block.Block {
        CallbackFloor() { super(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()); }
        @Override protected void neighborChanged(net.minecraft.world.level.block.state.BlockState state,net.minecraft.world.level.Level level,BlockPos pos,
                net.minecraft.world.level.block.Block changed,BlockPos from,boolean moving) {
            if(level.getBlockState(from).getBlock() instanceof CasinoBlock) level.removeBlock(from.west(),false);
        }
    }
    @net.neoforged.bus.api.SubscribeEvent public static void register(net.neoforged.neoforge.registries.RegisterEvent event) {
        if(event.getRegistryKey().equals(net.minecraft.core.registries.Registries.BLOCK)) event.register(net.minecraft.core.registries.Registries.BLOCK,h->h.register(ROLLBACK,new CallbackFloor()));
    }
    private static BlockPos fixture(GameTestHelper h) {
        for(int x=0;x<9;x++) for(int z=0;z<9;z++) {
            h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
            for(int y=1;y<=4;y++) h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
        }
        return h.absolutePos(new BlockPos(4,1,4));
    }
    private static ServerPlayer player(GameTestHelper h,BlockPos anchor,Direction facing,boolean creative) {
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"CasinoPlacement"));
        p.setGameMode(creative?GameType.CREATIVE:GameType.SURVIVAL);
        p.setPos(anchor.getX()+4.5,anchor.getY(),anchor.getZ()+4.5);
        p.setYRot(facing.getOpposite().toYRot());return p;
    }
    private static BlockHitResult hit(BlockPos anchor) {
        return new BlockHitResult(Vec3.atBottomCenterOf(anchor),Direction.UP,anchor.below(),false);
    }
    private static ItemStack equip(ServerPlayer p,String kind) {
        var stack=new ItemStack(CasinoContent.TABLE_BLOCKS.get(kind).get().asItem(),2);
        p.setItemInHand(InteractionHand.MAIN_HAND,stack);return stack;
    }
    private static void complete(GameTestHelper h,BlockPos anchor,String kind,Direction facing) {
        var state=h.getLevel().getBlockState(anchor);
        h.assertTrue(state.is(CasinoContent.TABLE_BLOCKS.get(kind).get()),kind+" anchor");
        h.assertTrue(state.getValue(CasinoBlock.FACING)==facing,kind+" orientation "+facing);
        h.assertTrue(state.getValue(CasinoBlock.EXPANDED)==StationLayout.enlarged(kind),kind+" footprint state");
        h.assertTrue(StationStructure.complete(h.getLevel(),anchor,state),kind+" all parts");
        for(var part:StationLayout.parts(kind)) {
            var pos=StationStructure.partPos(anchor,part,facing);
            h.assertTrue(anchor.equals(StationStructure.anchor(h.getLevel(),pos)),kind+" correct ownership");
        }
    }
    @GameTest(template="empty",timeoutTicks=80)
    public static void everyItemPlacesThroughRightClickInEveryDirectionSurvivalAndCreative(GameTestHelper h) {
        BlockPos anchor=fixture(h);
        for(String kind:CasinoContent.TABLE_BLOCKS.keySet()) for(Direction facing:Direction.Plane.HORIZONTAL) for(boolean creative:new boolean[]{false,true}) {
            var p=player(h,anchor,facing,creative);var stack=equip(p,kind);
            var result=p.gameMode.useItemOn(p,h.getLevel(),stack,InteractionHand.MAIN_HAND,hit(anchor));
            h.assertTrue(result.consumesAction(),kind+" actual right click "+facing+" creative="+creative);
            complete(h,anchor,kind,facing);
            h.assertTrue(stack.getCount()==(creative?2:1),"Correct item consumption");
            h.getLevel().removeBlock(anchor,false);
            for(var part:StationLayout.parts(kind)) h.assertTrue(h.getLevel().getBlockState(StationStructure.partPos(anchor,part,facing)).isAir(),"No remnants");
            p.getInventory().clearContent();
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=80)
    public static void obstaclesAtEveryCellRejectWithoutConsumptionOrPartialStructure(GameTestHelper h) {
        BlockPos anchor=fixture(h);
        for(String kind:CasinoContent.TABLE_BLOCKS.keySet()) for(Direction facing:Direction.Plane.HORIZONTAL) {
            var p=player(h,anchor,facing,false);
            var targets=new java.util.ArrayList<BlockPos>();targets.add(anchor);
            for(var part:StationLayout.parts(kind)) targets.add(StationStructure.partPos(anchor,part,facing));
            for(var obstacle:targets) {
                h.getLevel().setBlockAndUpdate(obstacle,Blocks.STONE.defaultBlockState());
                var stack=equip(p,kind);var item=(StationItem)stack.getItem();
                var context=new BlockPlaceContext(p,InteractionHand.MAIN_HAND,stack,hit(anchor));
                var result=item.place(context);
                h.assertTrue(!result.consumesAction() && stack.getCount()==2,"Obstacle rejects "+kind+" at "+obstacle);
                for(var target:targets) if(!target.equals(obstacle)) h.assertTrue(h.getLevel().getBlockState(target).isAir(),"No half-built cells");
                h.assertTrue(h.getLevel().getBlockState(obstacle).is(Blocks.STONE),"Obstacle remains intact");
                h.getLevel().removeBlock(obstacle,false);
            }
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=80)
    public static void replaceablePlantsFloorHeadroomAndEntityChecksUseFinalFootprint(GameTestHelper h) {
        BlockPos anchor=fixture(h);var level=h.getLevel();
        for(String kind:CasinoContent.TABLE_BLOCKS.keySet()) {
            var p=player(h,anchor,Direction.NORTH,false);var stack=equip(p,kind);
            var context=new BlockPlaceContext(p,InteractionHand.MAIN_HAND,stack,hit(anchor));
            var state=CasinoContent.TABLE_BLOCKS.get(kind).get().getStateForPlacement(context).setValue(CasinoBlock.EXPANDED,StationLayout.enlarged(kind));
            level.removeBlock(anchor.below(),false);
            h.assertTrue(StationStructure.failure(context,state)!=null,"Missing floor rejected: "+kind);
            h.assertTrue(!((StationItem)stack.getItem()).place(context).consumesAction() && stack.getCount()==2,"Missing floor is atomic");
            level.setBlockAndUpdate(anchor.below(),Blocks.STONE.defaultBlockState());
            for(var part:StationLayout.parts(kind)) if(part.seat()>=0) {
                BlockPos head=StationStructure.partPos(anchor,part,Direction.NORTH).above(2);
                level.setBlockAndUpdate(head,Blocks.STONE.defaultBlockState());
                h.assertTrue(StationStructure.failure(context,state)!=null,"Chair headroom rejected");level.removeBlock(head,false);
            }
            var entity=new net.minecraft.world.entity.decoration.ArmorStand(level,anchor.getX()+.5,anchor.getY(),anchor.getZ()+.5);
            level.addFreshEntity(entity);
            h.assertTrue(StationStructure.failure(context,state)!=null,"Entity collision rejected: "+kind);entity.discard();
            for(var part:StationLayout.parts(kind)) if(part.y()==0) {
                BlockPos pos=StationStructure.partPos(anchor,part,Direction.NORTH);
                level.setBlock(pos.below(),Blocks.GRASS_BLOCK.defaultBlockState(),3);
                level.setBlock(pos,Blocks.SHORT_GRASS.defaultBlockState(),3);
            }
            h.assertTrue(((StationItem)stack.getItem()).place(context).consumesAction(),"Replaceable plants accepted: "+kind);
            complete(h,anchor,kind,Direction.NORTH);level.removeBlock(anchor,false);
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=80)
    public static void usingCasinoItemOnExistingStationReachesItemInsteadOfOpeningMenu(GameTestHelper h) {
        BlockPos anchor=fixture(h),existing=anchor.below();var level=h.getLevel();
        level.setBlockAndUpdate(existing,CasinoContent.TABLE_BLOCKS.get("exchange").get().defaultBlockState());
        var p=player(h,anchor,Direction.NORTH,false);var stack=equip(p,"poker");
        var result=p.gameMode.useItemOn(p,level,stack,InteractionHand.MAIN_HAND,hit(anchor));
        h.assertTrue(result.consumesAction(),"Placement interaction consumed");complete(h,anchor,"poker",Direction.NORTH);
        h.assertTrue(stack.getCount()==1 && p.containerMenu==p.inventoryMenu,"Placed item, did not open casino menu");
        level.removeBlock(anchor,false);level.removeBlock(existing,false);h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=80)
    public static void dismantlingPartsDropsExactlyOneItemSurvivalAndNoneCreativeAndCanReplace(GameTestHelper h) {
        BlockPos anchor=fixture(h);var level=h.getLevel();
        var bounds=new net.minecraft.world.phys.AABB(anchor).inflate(5);
        for(String kind:new String[]{"blackjack","poker","roulette","wheel"}) for(boolean creative:new boolean[]{false,true}) {
            var p=player(h,anchor,Direction.WEST,creative);var stack=equip(p,kind);
            h.assertTrue(((StationItem)stack.getItem()).place(new BlockPlaceContext(p,InteractionHand.MAIN_HAND,stack,hit(anchor))).consumesAction(),"Initial placement");
            BlockPos target=StationStructure.partPos(anchor,StationLayout.parts(kind).getLast(),Direction.WEST);
            h.assertTrue(p.gameMode.destroyBlock(target),"Actual player dismantles part");
            h.assertTrue(level.getBlockState(anchor).isAir(),"Anchor removed");
            for(var part:StationLayout.parts(kind)) h.assertTrue(level.getBlockState(StationStructure.partPos(anchor,part,Direction.WEST)).isAir(),"No remnant parts");
            var drops=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,bounds);
            h.assertTrue(drops.stream().mapToInt(e->e.getItem().getCount()).sum()==(creative?0:1),"Exactly one survival drop, no creative drop: "+kind);
            drops.forEach(net.minecraft.world.entity.Entity::discard);
            stack=equip(p,kind);
            h.assertTrue(((StationItem)stack.getItem()).place(new BlockPlaceContext(p,InteractionHand.MAIN_HAND,stack,hit(anchor))).consumesAction(),"Re-placement");
            complete(h,anchor,kind,Direction.WEST);level.removeBlock(anchor,false);
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=80)
    public static void loadedChunkBoundaryAndDeferredRemovalKeepOwnership(GameTestHelper h) {
        BlockPos base=fixture(h);var level=h.getLevel();
        BlockPos anchor=new BlockPos((base.getX()>>4)*16+15,base.getY(),base.getZ());
        for(int x=-2;x<=2;x++) for(int z=-3;z<=3;z++) {
            BlockPos floor=anchor.offset(x,-1,z);level.setBlockAndUpdate(floor,Blocks.STONE.defaultBlockState());
            for(int y=1;y<=4;y++) level.setBlockAndUpdate(floor.above(y),Blocks.AIR.defaultBlockState());
        }
        var p=player(h,anchor,Direction.NORTH,false);var stack=equip(p,"roulette");
        h.assertTrue(((StationItem)stack.getItem()).place(new BlockPlaceContext(p,InteractionHand.MAIN_HAND,stack,hit(anchor))).consumesAction(),"Loaded chunk boundary allowed");
        complete(h,anchor,"roulette",Direction.NORTH);
        var layout=StationLayout.parts("roulette");var part=layout.get(0);var pos=StationStructure.partPos(anchor,part,Direction.NORTH);var expected=StationStructure.partState("roulette",0,Direction.NORTH);
        // Simulate a removal queued while this cell's chunk was unloaded, then reload its saved queue.
        var pending=PendingStationRemoval.get(level);pending.remember(pos,expected);
        var tag=pending.save(new net.minecraft.nbt.CompoundTag(),level.registryAccess());
        h.assertTrue(tag.getList("parts",10).size()>0,"Deferred ownership persisted");
        level.removeBlock(anchor,false);
        level.setBlockAndUpdate(pos,Blocks.DIAMOND_BLOCK.defaultBlockState());pending.clean(level,new net.minecraft.world.level.ChunkPos(pos));
        h.assertTrue(level.getBlockState(pos).is(Blocks.DIAMOND_BLOCK),"Cleanup must not remove foreign replacement");
        level.removeBlock(pos,false);
        for(boolean creative:new boolean[]{false,true}) {
            stack=equip(p,"roulette");
            h.assertTrue(((StationItem)stack.getItem()).place(new BlockPlaceContext(p,InteractionHand.MAIN_HAND,stack,hit(anchor))).consumesAction(),"Deferred owner fixture");
            var owner=level.getBlockState(anchor);
            pending.rememberOwner(anchor,owner,!creative);
            pending.rememberOwner(anchor,owner,true); // Generic removal must preserve the Creative decision.
            var restored=PendingStationRemoval.load(pending.save(new net.minecraft.nbt.CompoundTag(),level.registryAccess()),level.registryAccess());
            restored.clean(level,new net.minecraft.world.level.ChunkPos(anchor));
            h.assertTrue(level.getBlockState(anchor).isAir(),"Reloaded queue removes owner");
            for(var offset:layout) h.assertTrue(level.getBlockState(StationStructure.partPos(anchor,offset,Direction.NORTH)).isAir(),"Deferred removal cleans all parts");
            var drops=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(anchor).inflate(5));
            h.assertTrue(drops.stream().mapToInt(e->e.getItem().getCount()).sum()==(creative?0:1),"Deferred drop count survived restart");
            drops.forEach(net.minecraft.world.entity.Entity::discard);
            pending.clean(level,new net.minecraft.world.level.ChunkPos(anchor));
        }
        level.setBlockAndUpdate(pos,expected);pending.remember(pos,expected);
        var restored=PendingStationRemoval.load(pending.save(new net.minecraft.nbt.CompoundTag(),level.registryAccess()),level.registryAccess());
        restored.clean(level,new net.minecraft.world.level.ChunkPos(pos));
        h.assertTrue(level.getBlockState(pos).isAir(),"Reloaded queue removes exact orphaned part");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=80)
    public static void neighbourMutationRollsBackAndRestoresPlantsWithoutItemsOrDrops(GameTestHelper h) {
        BlockPos anchor=fixture(h);var level=h.getLevel();var p=player(h,anchor,Direction.NORTH,false);var stack=equip(p,"blackjack");
        for(var part:StationLayout.parts("blackjack")) if(part.y()==0) {
            BlockPos pos=StationStructure.partPos(anchor,part,Direction.NORTH);
            level.setBlockAndUpdate(pos.below(),Blocks.GRASS_BLOCK.defaultBlockState());level.setBlockAndUpdate(pos,Blocks.SHORT_GRASS.defaultBlockState());
        }
        level.setBlockAndUpdate(anchor.below(),net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ROLLBACK).defaultBlockState());
        var result=((StationItem)stack.getItem()).place(new BlockPlaceContext(p,InteractionHand.MAIN_HAND,stack,hit(anchor)));
        h.assertTrue(!result.consumesAction() && stack.getCount()==2,"Changed area rolls back without item consumption");
        h.assertTrue(level.getBlockState(anchor).isAir(),"No invisible owner");
        for(var part:StationLayout.parts("blackjack")) h.assertTrue(level.getBlockState(StationStructure.partPos(anchor,part,Direction.NORTH)).is(Blocks.SHORT_GRASS),"All replaced plants restored");
        h.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(anchor).inflate(5)).isEmpty(),"Rollback has no duplicated drops");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=80)
    public static void legacyItemStateUsesCheckedFootprintAndAdventureDoesNotConsume(GameTestHelper h) {
        BlockPos anchor=fixture(h);var level=h.getLevel();var p=player(h,anchor,Direction.NORTH,false);var stack=equip(p,"wheel");
        var old=net.minecraft.world.item.component.BlockItemStateProperties.EMPTY.with(CasinoBlock.EXPANDED,false).with(CasinoBlock.FACING,Direction.WEST);
        stack.set(net.minecraft.core.component.DataComponents.BLOCK_STATE,old);
        h.assertTrue(((StationItem)stack.getItem()).place(new BlockPlaceContext(p,InteractionHand.MAIN_HAND,stack,hit(anchor))).consumesAction(),"Legacy item places correct footprint");
        complete(h,anchor,"wheel",Direction.WEST);
        h.assertTrue(stack.get(net.minecraft.core.component.DataComponents.BLOCK_STATE).equals(old),"Original item metadata preserved");
        level.removeBlock(anchor,false);p.setGameMode(GameType.ADVENTURE);stack=equip(p,"wheel");
        h.assertTrue(!((StationItem)stack.getItem()).place(new BlockPlaceContext(p,InteractionHand.MAIN_HAND,stack,hit(anchor))).consumesAction() && stack.getCount()==2,"No build permission rejects without consumption");
        h.assertTrue(level.getBlockState(anchor).isAir(),"Adventure leaves no parts");h.succeed();
    }

}
