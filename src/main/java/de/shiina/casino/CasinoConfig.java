package de.shiina.casino;

import de.shiina.casino.logic.Rules;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class CasinoConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.IntValue RATE = BUILDER.defineInRange("diamondChipRate", 100, 1, 10000);
    public static final ModConfigSpec.IntValue MIN = BUILDER.defineInRange("minimumBet", 2, 1, 1000000);
    public static final ModConfigSpec.IntValue MAX = BUILDER.defineInRange("maximumBet", 10000, 1, 1000000);
    public static final ModConfigSpec.IntValue BUY_MIN = BUILDER.defineInRange("pokerBuyInMinimum", 20, 1, 1000000);
    public static final ModConfigSpec.IntValue BUY_MAX = BUILDER.defineInRange("pokerBuyInMaximum", 10000, 1, 1000000);
    public static final ModConfigSpec.IntValue SMALL = BUILDER.defineInRange("smallBlind", 1, 1, 1000000);
    public static final ModConfigSpec.IntValue BIG = BUILDER.defineInRange("bigBlind", 2, 1, 1000000);
    public static final ModConfigSpec.IntValue BET_TIME = BUILDER.defineInRange("bettingSeconds", 20, 1, 300);
    public static final ModConfigSpec.IntValue TURN_TIME = BUILDER.defineInRange("turnSeconds", 30, 1, 300);
    public static final ModConfigSpec.DoubleValue RANGE = BUILDER.defineInRange("participationRange", 8.0, 2.0, 32.0);
    public static final ModConfigSpec.IntValue WATCHERS = BUILDER.defineInRange("spectatorLimit", 12, 0, 32);
    public static final ModConfigSpec.IntValue TABLES = BUILDER.defineInRange("activeTableLimit", 32, 1, 256);
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> WHEEL = BUILDER.comment("Legacy setting retained for existing configs. The physical wheel always uses 6x0, 3x1, 2x2, 1x4.").defineList("wheelMultipliers",
            List.of(0, 0, 0, 0, 0, 0, 1, 1, 1, 2, 2, 4), value -> value instanceof Integer number && number >= 0 && number <= 1000);
    public static final ModConfigSpec.BooleanValue BLACKJACK = BUILDER.define("enableBlackjack", true);
    public static final ModConfigSpec.BooleanValue POKER = BUILDER.define("enablePoker", true);
    public static final ModConfigSpec.BooleanValue ROULETTE = BUILDER.define("enableRoulette", true);
    public static final ModConfigSpec.BooleanValue FORTUNE = BUILDER.define("enableWheel", true);
    public static final ModConfigSpec SPEC = BUILDER.build();

    private CasinoConfig() {}
    public static Rules snapshot() {
        return new Rules(RATE.get(), MIN.get(), MAX.get(), BUY_MIN.get(), BUY_MAX.get(), SMALL.get(), BIG.get(),
                BET_TIME.get(), TURN_TIME.get(), RANGE.get(), WATCHERS.get(), TABLES.get(),
                Rules.WHEEL_SEGMENTS, BLACKJACK.get(), POKER.get(), ROULETTE.get(), FORTUNE.get());
    }
}
