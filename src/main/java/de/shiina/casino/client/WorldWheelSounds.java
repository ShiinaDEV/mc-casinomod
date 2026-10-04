package de.shiina.casino.client;

import de.shiina.casino.block.WheelBlockEntity;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public final class WorldWheelSounds {
    private static final Map<WheelBlockEntity,Long> ACTIVE=new HashMap<>();
    public static void update(WheelBlockEntity wheel) {
        if(wheel.spinning && wheel.getLevel()!=null && wheel.getLevel().getGameTime()<wheel.startedTick+wheel.duration) ACTIVE.putIfAbsent(wheel,-1L);
        else if(ACTIVE.remove(wheel)!=null) stop(wheel);
    }
    public static void tick(ClientTickEvent.Post event) {
        var minecraft=Minecraft.getInstance();
        ACTIVE.entrySet().removeIf(entry -> {
            var wheel=entry.getKey(); var level=minecraft.level;
            if(level==null || wheel.getLevel()!=level || wheel.isRemoved() || !level.hasChunkAt(wheel.getBlockPos())) return true;
            long now=level.getGameTime();
            if(now>=wheel.startedTick+wheel.duration) { stop(wheel); return true; }
            if(now-entry.getValue()>=6 && minecraft.player!=null && minecraft.player.distanceToSqr(wheel.getBlockPos().getCenter())<=256) {
                var pos=wheel.getBlockPos();
                if (CasinoClientConfig.SOUNDS.get()) level.playLocalSound(pos.getX()+.5,pos.getY()+1.5,pos.getZ()+.5,SoundEvents.WOODEN_BUTTON_CLICK_ON,SoundSource.BLOCKS,.22F*CasinoClientConfig.VOLUME.get().floatValue(),1.2F,false);
                entry.setValue(now);
            }
            return false;
        });
    }
    private static void stop(WheelBlockEntity wheel) {
        var mc=Minecraft.getInstance();var pos=wheel.getBlockPos();
        if(CasinoClientConfig.SOUNDS.get() && mc.level==wheel.getLevel() && mc.player!=null && mc.player.distanceToSqr(pos.getCenter())<=256)
            mc.level.playLocalSound(pos.getX()+.5,pos.getY()+1.5,pos.getZ()+.5,SoundEvents.EXPERIENCE_ORB_PICKUP,SoundSource.BLOCKS,.4F*CasinoClientConfig.VOLUME.get().floatValue(),.9F,false);
    }
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { ACTIVE.clear(); }
    private WorldWheelSounds() {}
}
