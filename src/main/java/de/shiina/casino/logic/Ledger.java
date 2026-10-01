package de.shiina.casino.logic;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Ledger {
    public record Obligation(String location, String game, long round, Map<UUID, Long> recovery) {
        public Obligation { recovery = Map.copyOf(recovery); }
    }

    private final Map<UUID, Long> balances = new HashMap<>();
    private final Map<UUID, Obligation> pending = new HashMap<>();
    private final Runnable dirty;

    public Ledger(Runnable dirty) { this.dirty = dirty; }
    public Map<UUID, Long> balances() { return Map.copyOf(balances); }
    public Map<UUID, Obligation> pending() { return Map.copyOf(pending); }
    public long balance(UUID player) { return balances.getOrDefault(player, 0L); }

    public void credit(UUID player, long amount) {
        long total = Money.add(balance(player), amount);
        if (total > 0) balances.put(player, total);
        dirty.run();
    }

    public void restoreBalance(UUID player, long amount) {
        if (Money.checked(amount) > 0) balances.put(player, amount);
    }

    public void hold(UUID transaction, String location, String game, long round, Map<UUID, Long> recovery) {
        recovery.values().forEach(Money::checked);
        pending.put(transaction, new Obligation(location, game, round, recovery));
        dirty.run();
    }

    public boolean settle(UUID transaction) {
        Obligation obligation = pending.get(transaction);
        if (obligation == null) return false;
        obligation.recovery.forEach((player, amount) -> Money.add(balance(player), amount));
        obligation.recovery.forEach(this::credit);
        pending.remove(transaction);
        dirty.run();
        return true;
    }

    public void recoverAll() { for (UUID transaction : pending.keySet().toArray(UUID[]::new)) settle(transaction); }

    public long claim(UUID player, long capacity, java.util.function.LongUnaryOperator deliver) {
        long requested = Math.min(balance(player), Money.checked(capacity));
        long delivered = deliver.applyAsLong(requested);
        if (delivered < 0 || delivered > requested) throw new IllegalStateException("invalid delivery");
        long remaining = balance(player) - delivered;
        if (remaining == 0) balances.remove(player); else balances.put(player, remaining);
        dirty.run();
        return delivered;
    }
}
