package de.shiina.casino.logic;

import java.util.List;

public record Rules(int exchangeRate, long minBet, long maxBet, long buyInMin, long buyInMax,
                    long smallBlind, long bigBlind, int bettingSeconds, int turnSeconds,
                    double range, int spectators, int maxTables, List<Integer> wheel,
                    boolean blackjack, boolean poker, boolean roulette, boolean fortune) {
    public static final List<Integer> WHEEL_SEGMENTS = List.of(0,0,0,0,0,0,1,1,1,2,2,4);
    public Rules {
        wheel = List.copyOf(wheel);
        if (exchangeRate < 1 || exchangeRate > 10000 || minBet < 1 || maxBet < minBet || maxBet > 1000000
                || buyInMin < bigBlind || buyInMax < buyInMin || buyInMax > 1000000
                || smallBlind < 1 || bigBlind < smallBlind || bigBlind > 1000000
                || bettingSeconds < 1 || turnSeconds < 1 || range < 2 || range > 32
                || spectators < 0 || spectators > 32 || maxTables < 1 || maxTables > 256
                || wheel.size() < 2 || wheel.size() > 24
                || wheel.stream().anyMatch(value -> value < 0 || value > 1000))
            throw new IllegalArgumentException("casino.error.config");
    }

    public static Rules defaults() {
        return new Rules(100, 2, 10000, 20, 10000, 1, 2, 20, 30, 8, 12, 32,
                List.of(0, 0, 0, 0, 0, 0, 1, 1, 1, 2, 2, 4), true, true, true, true);
    }

    public void bet(long amount, boolean even) {
        if (amount < minBet || amount > maxBet || (even && amount % 2 != 0))
            throw new IllegalArgumentException(even ? "casino.error.even" : "casino.error.bet");
    }
}
