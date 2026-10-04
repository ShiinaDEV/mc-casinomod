package de.shiina.casino.logic;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class TestBank implements Game.Bank {
    final Ledger ledger = new Ledger(() -> {});
    final Map<UUID, Long> inventory = new HashMap<>();
    final Map<UUID, Long> debits = new HashMap<>();
    final Map<UUID, Long> maximumHeld = new HashMap<>();
    final Map<UUID,Integer> seats = new HashMap<>();
    final PersonalRounds personal = new PersonalRounds();
    final Map<UUID, java.util.List<PersonalRounds.Result>> completions = new HashMap<>();
    boolean allowed = true;

    UUID player(long chips) { UUID id = UUID.randomUUID(); inventory.put(id, chips); personal.open(id); return id; }
    public boolean debit(UUID player, long amount) {
        long available = inventory.getOrDefault(player, 0L);
        if (available < amount) return false;
        inventory.put(player, available - amount); debits.merge(player, amount, Money::add); return true;
    }
    public void credit(UUID player, long amount) { ledger.credit(player, amount); }
    public void hold(UUID transaction, String game, long round, Map<UUID, Long> recovery) {
        ledger.hold(transaction, "test", game, round, recovery);
        recovery.forEach((player, amount) -> maximumHeld.merge(player, amount, Math::max));
    }
    public void settle(UUID transaction) { ledger.settle(transaction); }
    public boolean available(UUID player) { return allowed; }
    public String name(UUID player) { return player.toString().substring(0, 6); }
    public int seat(UUID player) { return seats.getOrDefault(player,-1); }
    public void completed(UUID player, PersonalRounds.Result result) {
        completions.computeIfAbsent(player, ignored -> new java.util.ArrayList<>()).add(result);
        personal.complete(player, result);
    }
    public void rememberBet(UUID player, String game, long amount) { personal.remember(player, game, amount); }
    public void rememberWagers(UUID player, java.util.List<PersonalRounds.Wager> wagers) { personal.rememberWagers(player, wagers); }
    public java.util.List<PersonalRounds.Wager> lastWagers(UUID player) { return personal.lastWagers(player); }
}
