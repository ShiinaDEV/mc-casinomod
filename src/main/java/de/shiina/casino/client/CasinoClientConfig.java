package de.shiina.casino.client;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CasinoClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue SOUNDS = BUILDER.comment("Menu effects respect Minecraft Master volume; world wheel sounds respect Blocks volume.").define("casinoSounds", true);
    public static final ModConfigSpec.DoubleValue VOLUME = BUILDER.defineInRange("casinoSoundVolume", 0.5, 0.0, 1.0);
    public static final ModConfigSpec.BooleanValue REDUCED = BUILDER.define("reducedAnimations", false);
    public static final ModConfigSpec SPEC = BUILDER.build();
    private CasinoClientConfig() {}
}
