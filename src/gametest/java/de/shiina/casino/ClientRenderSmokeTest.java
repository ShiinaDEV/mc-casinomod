package de.shiina.casino;

import com.mojang.blaze3d.vertex.PoseStack;
import de.shiina.casino.block.CasinoBlock;
import de.shiina.casino.block.WheelBlockEntity;
import de.shiina.casino.client.WorldWheelRenderer;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Development-only: real integrated world, GPU, Font, dispatcher and shared buffers. Never packaged. */
@EventBusSubscriber(modid=ShiinaCasino.ID,value=Dist.CLIENT)
public final class ClientRenderSmokeTest {
    private static final boolean ENABLED=Boolean.getBoolean("shiinacasino.renderTest");
    private static final Map<String,Integer> rendered=new HashMap<>();
    private static boolean started,sceneRequested,finished;
    private static volatile boolean sceneReady;
    private static int ticks,spinningFrames;
    private static double firstAngle=Double.NaN,lastAngle;
    private static final long start=System.nanoTime();
    @EventBusSubscriber(modid=ShiinaCasino.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent(priority=EventPriority.LOWEST)
        public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            if(ENABLED) event.registerBlockEntityRenderer(CasinoContent.WORLD_WHEEL.get(),context->new Probe(new WorldWheelRenderer(context)));
        }
    }
    private record Probe(WorldWheelRenderer actual) implements BlockEntityRenderer<WheelBlockEntity> {
        @Override public boolean shouldRenderOffScreen(WheelBlockEntity wheel) { return actual.shouldRenderOffScreen(wheel); }
        @Override public int getViewDistance() { return actual.getViewDistance(); }
        @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(WheelBlockEntity wheel) { return actual.getRenderBoundingBox(wheel); }
        @Override public void render(WheelBlockEntity wheel,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
            actual.render(wheel,partial,pose,buffers,light,overlay);
            var state=wheel.getBlockState();String kind=((CasinoBlock)state.getBlock()).kind;
            rendered.merge(kind+":"+state.getValue(CasinoBlock.EXPANDED)+":"+state.getValue(CasinoBlock.FACING),1,Integer::sum);
            if(wheel.spinning) {
                spinningFrames++;
                double angle=de.shiina.casino.logic.WheelAnimation.angle(wheel.result,kind.equals("wheel")?12:37,wheel.getLevel().getGameTime()+partial-wheel.startedTick,wheel.duration);
                if(Double.isNaN(firstAngle)) firstAngle=angle;lastAngle=angle;
            }
        }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!ENABLED || finished) return;
        var mc=Minecraft.getInstance();
        if((System.nanoTime()-start)/1_000_000_000L>180) throw new IllegalStateException("Client render smoke test timed out: "+rendered);
        if(!started && mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
            mc.options.onboardAccessibility=false;mc.options.save();mc.setScreen(new TitleScreen());
        }
        if(!started && mc.screen instanceof TitleScreen) {
            started=true;mc.options.renderDistance().set(4);mc.options.simulationDistance().set(4);
            var settings=new LevelSettings("Casino renderer regression",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel("renderer-"+System.currentTimeMillis(),settings,new WorldOptions(20261001,false,false),
                    registry->registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);
        }
        if(mc.level==null || mc.player==null || mc.getSingleplayerServer()==null) return;
        if(!sceneRequested) {
            sceneRequested=true;var id=mc.player.getUUID();
            mc.getSingleplayerServer().execute(()->{
                var server=mc.getSingleplayerServer();var level=server.overworld();var player=server.getPlayerList().getPlayer(id);
                BlockPos base=player.blockPosition().offset(0,4,8);
                for(int x=-10;x<=10;x++) for(int z=-8;z<=18;z++) {
                    level.setBlockAndUpdate(base.offset(x,-1,z),Blocks.STONE.defaultBlockState());
                    for(int y=0;y<=4;y++) level.setBlockAndUpdate(base.offset(x,y,z),Blocks.AIR.defaultBlockState());
                }
                int row=0;
                for(String kind:new String[]{"wheel","roulette"}) for(boolean expanded:new boolean[]{false,true}) {
                    int column=0;
                    for(Direction facing:Direction.Plane.HORIZONTAL) {
                        BlockPos pos=base.offset(-6+column++*4,0,row*4);
                        var state=CasinoContent.TABLE_BLOCKS.get(kind).get().defaultBlockState().setValue(CasinoBlock.FACING,facing).setValue(CasinoBlock.EXPANDED,expanded);
                        level.setBlockAndUpdate(pos,state);
                        var wheel=(WheelBlockEntity)level.getBlockEntity(pos);
                        wheel.animation(7,false,level.getGameTime(),160,3);
                        // Reload the persisted block-entity representation, including the existing IDs.
                        var tag=wheel.saveWithFullMetadata(level.registryAccess());
                        var restored=(WheelBlockEntity)BlockEntity.loadStatic(pos,state,tag,level.registryAccess());
                        if(restored==null || restored.result!=3 || restored.round!=7) throw new IllegalStateException("Wheel NBT round-trip failed");
                        level.setBlockEntity(restored);
                        if(expanded) restored.animation(8,true,level.getGameTime(),160,3);
                        level.sendBlockUpdated(pos,state,state,3);
                    }
                    row++;
                }
                player.teleportTo(server.overworld(),base.getX()+.5,base.getY()+2,base.getZ()-6,0,8);
                sceneReady=true;
            });
        }
        if(!sceneReady) return;
        if(mc.screen!=null) mc.setScreen(null);
        if(++ticks<200) return;
        boolean wheelsComplete=true;
        for(boolean expanded:new boolean[]{false,true}) for(Direction facing:Direction.Plane.HORIZONTAL)
            wheelsComplete &= rendered.getOrDefault("wheel:"+expanded+":"+facing,0)>=20;
        boolean rouletteComplete=rendered.keySet().stream().anyMatch(k->k.startsWith("roulette:true:")) && rendered.keySet().stream().anyMatch(k->k.startsWith("roulette:false:"));
        if(!wheelsComplete || !rouletteComplete || spinningFrames<20 || Math.abs(lastAngle-firstAngle)<.01)
            throw new IllegalStateException("Insufficient actual world rendering: "+rendered+" spinFrames="+spinningFrames);
        try {
            Files.writeString(mc.gameDirectory.toPath().resolve("client-render-result.txt"),"PASS: real world dispatcher/GPU/font/shared-buffer rendering; all eight fortune-wheel combinations (legacy/expanded, all directions), roulette legacy/expanded, reloaded NBT, concurrent wheels, moving animation.\n"+rendered+"\nspinningFrames="+spinningFrames+" angles="+firstAngle+","+lastAngle+"\n");
        } catch(java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        finished=true;System.out.println("CASINO CLIENT RENDER TEST PASSED: "+rendered);
        net.minecraft.client.Screenshot.grab(mc.gameDirectory,mc.getMainRenderTarget(),message->{System.out.println(message.getString());mc.execute(()->mc.stop());});
    }
}
