package de.shiina.casino.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

public final class CasinoEffects {
    private CasinoEffects() {}
    public static void play(SoundEvent sound, float pitch, float volume) {
        if (CasinoClientConfig.SOUNDS.get()) Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(sound, pitch, volume * CasinoClientConfig.VOLUME.get().floatValue()));
    }
}
