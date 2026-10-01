package de.shiina.casino.logic;

import java.util.Set;

public final class Roulette {
    public static final int[] ORDER = {0,32,15,19,4,21,2,25,17,34,6,27,13,36,11,30,8,23,10,5,24,16,33,1,20,14,31,9,22,18,29,7,28,12,35,3,26};
    private static final Set<Integer> RED = Set.of(1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36);
    public enum Kind { NUMBER, RED, BLACK, EVEN, ODD, LOW, HIGH, DOZEN, COLUMN }
    private Roulette() {}
    public static boolean red(int number) { return RED.contains(number); }
    public static void validate(Kind kind, int number) {
        if ((kind == Kind.NUMBER && (number < 0 || number > 36))
                || ((kind == Kind.DOZEN || kind == Kind.COLUMN) && (number < 1 || number > 3)))
            throw new IllegalArgumentException("casino.error.bet");
    }
    public static long payout(Kind kind, int number, long amount, int result) {
        validate(kind, number); Money.checked(amount);
        if (result < 0 || result > 36) throw new IllegalArgumentException("roulette result");
        boolean win = kind == Kind.NUMBER ? result == number : result != 0 && switch (kind) {
            case RED -> red(result);
            case BLACK -> !red(result);
            case EVEN -> result % 2 == 0;
            case ODD -> result % 2 == 1;
            case LOW -> result <= 18;
            case HIGH -> result >= 19;
            case DOZEN -> (result - 1) / 12 + 1 == number;
            case COLUMN -> (result - 1) % 3 + 1 == number;
            default -> false;
        };
        int multiplier = kind == Kind.NUMBER ? 36 : kind == Kind.DOZEN || kind == Kind.COLUMN ? 3 : 2;
        return win ? Money.multiply(amount, multiplier) : 0;
    }
}
