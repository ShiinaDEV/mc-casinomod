package de.shiina.casino.logic;

public final class Money {
    public static final long LIMIT = 1_000_000_000_000L;

    private Money() {}

    public static long checked(long amount) {
        if (amount < 0 || amount > LIMIT) throw new IllegalArgumentException("casino.error.amount");
        return amount;
    }

    public static long add(long first, long second) {
        return checked(Math.addExact(checked(first), checked(second)));
    }

    public static long multiply(long amount, long multiplier) {
        return checked(Math.multiplyExact(checked(amount), checked(multiplier)));
    }
}
