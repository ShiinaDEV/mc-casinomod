package de.shiina.casino.logic;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RouletteGame extends Game {
    private record Wager(UUID id, UUID player, Roulette.Kind kind, int number, long amount) {}
    private final List<Wager> wagers = new ArrayList<>();
    private final SecureRandom random;
    private UUID transaction = UUID.randomUUID();
    private int result = -1;
    private boolean paid;

    public RouletteGame(Bank bank, Rules rules, SecureRandom random) { super(bank, rules); this.random = random; }
    @Override public String kind() { return "roulette"; }
    @Override public boolean contains(UUID player) { return wagers.stream().anyMatch(wager -> wager.player.equals(player)); }
    @Override public List<UUID> players() { return wagers.stream().map(Wager::player).distinct().toList(); }
    @Override public boolean funded() { return !wagers.isEmpty() && !paid; }

    private void journal(boolean outcome) {
        Map<UUID, Long> amounts = new LinkedHashMap<>();
        wagers.forEach(wager -> amounts.merge(wager.player,
                outcome ? Roulette.payout(wager.kind, wager.number, wager.amount, result) : wager.amount, Money::add));
        bank.hold(transaction, kind() + (outcome ? ":result=" + result : ":betting"), round, amounts);
    }

    @Override public void action(UUID player, String action, long amount, int option, long now) {
        require(phase.equals("betting"), "casino.error.betting_closed");
        if (action.equals("cancel")) {
            require(option >= 0 && option < wagers.size());
            Wager wager = wagers.get(option);
            require(wager.player.equals(player));
            wagers.remove(option); journal(false); bank.credit(player, wager.amount);
            if (wagers.isEmpty()) { bank.settle(transaction); deadline = 0; }
            return;
        }
        if (action.equals("leave")) {
            long refund = wagers.stream().filter(wager -> wager.player.equals(player)).mapToLong(Wager::amount).sum();
            require(refund > 0);
            wagers.removeIf(wager -> wager.player.equals(player)); journal(false); bank.credit(player, refund);
            if (wagers.isEmpty()) { bank.settle(transaction); deadline = 0; }
            return;
        }
        require(rules.roulette(), "casino.error.disabled");
        if (action.equals("repeat")) {
            List<PersonalRounds.Wager> template = bank.lastWagers(player);
            require(!template.isEmpty(), "casino.error.template");
            require(template.size() + wagers.stream().filter(wager -> wager.player.equals(player)).count() <= 12, "casino.error.wager_limit");
            require(contains(player) || players().size() < 16, "casino.error.tables");
            long total = 0;
            List<Wager> staged = new ArrayList<>();
            for (PersonalRounds.Wager wager : template) {
                Roulette.validate(wager.kind(), wager.number());
                rules.bet(wager.amount(), false);
                total = Money.add(total, wager.amount());
                staged.add(new Wager(UUID.randomUUID(), player, wager.kind(), wager.number(), wager.amount()));
            }
            debit(player, total);
            wagers.addAll(staged); journal(false);
            if (deadline == 0) { startedAt = now; deadline = now + rules.bettingSeconds() * 1000L; }
            return;
        }
        require(action.startsWith("roulette_"));
        Roulette.Kind kind;
        try { kind = Roulette.Kind.valueOf(action.substring(9).toUpperCase(java.util.Locale.ROOT)); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("casino.error.action"); }
        Roulette.validate(kind, option); rules.bet(amount, false);
        require(wagers.stream().filter(wager -> wager.player.equals(player)).count() < 12, "casino.error.wager_limit");
        require(contains(player) || players().size() < 16);
        debit(player, amount);
        wagers.add(new Wager(UUID.randomUUID(), player, kind, option, amount)); journal(false);
        if (deadline == 0) { startedAt = now; deadline = now + rules.bettingSeconds() * 1000L; }
    }

    @Override public void timeout(long now) {
        if (deadline == 0 || now < deadline) return;
        if (phase.equals("betting")) {
            for (UUID player : players()) bank.rememberWagers(player, wagers.stream().filter(wager -> wager.player.equals(player))
                    .map(wager -> new PersonalRounds.Wager(wager.kind, wager.number, wager.amount)).toList());
            result = random.nextInt(37); journal(true);
            phase = "spinning"; startedAt = now; deadline = now + 5000;
        } else if (phase.equals("spinning")) {
            bank.settle(transaction); paid = true; phase = "result"; startedAt = now; deadline = now + 6000;
            recordResults();
        } else {
            wagers.clear(); result = -1; paid = false; phase = "betting"; deadline = 0;
            round++; transaction = UUID.randomUUID();
        }
        changedAt = now; revision++;
    }
    @Override public void depart(UUID player, long now) {}
    private void recordResults() {
        for (UUID player : players()) {
            long stake = 0, payout = 0;
            for (Wager wager : wagers) if (wager.player.equals(player)) {
                stake = Money.add(stake, wager.amount);
                payout = Money.add(payout, Roulette.payout(wager.kind, wager.number, wager.amount, result));
            }
            bank.completed(player, new PersonalRounds.Result(kind(), transaction, round, stake, payout));
        }
    }
    @Override public void close() {
        if (!wagers.isEmpty() && !paid) {
            bank.settle(transaction); paid = true;
            if (result >= 0) recordResults();
        }
        wagers.clear(); deadline = 0;
    }
    @Override public View view(UUID viewer, long now) {
        List<Bet> bets = wagers.stream().map(wager -> new Bet(wager.id.toString(), wager.player.toString(),
                wager.kind.name().toLowerCase(java.util.Locale.ROOT), wager.number, wager.amount)).toList();
        List<String> actions = new ArrayList<>();
        if (phase.equals("betting") && rules.roulette()) {
            if (wagers.stream().filter(wager -> wager.player.equals(viewer)).count() < 12 && (contains(viewer) || players().size() < 16)) actions.add("roulette");
            if (contains(viewer)) { actions.add("cancel"); actions.add("leave"); }
            int count = bank.lastWagers(viewer).size();
            if (count > 0 && count + wagers.stream().filter(wager -> wager.player.equals(viewer)).count() <= 12
                    && (contains(viewer) || players().size() < 16)) actions.add("repeat");
        }
        return view(List.of(), List.of(), List.of(), wagers.stream().mapToLong(Wager::amount).sum(),
                -1, result, List.of(), bets, actions, 0, 0, now);
    }
}
