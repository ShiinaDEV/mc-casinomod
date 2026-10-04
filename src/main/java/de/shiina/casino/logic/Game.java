package de.shiina.casino.logic;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public abstract class Game {
    public interface Bank {
        boolean debit(UUID player, long amount);
        void credit(UUID player, long amount);
        void hold(UUID transaction, String game, long round, Map<UUID, Long> recovery);
        void settle(UUID transaction);
        boolean available(UUID player);
        String name(UUID player);
        default int seat(UUID player) { return -1; }
        default void completed(UUID player, PersonalRounds.Result result) {}
        default void rememberBet(UUID player, String game, long amount) {}
        default void rememberWagers(UUID player, List<PersonalRounds.Wager> wagers) {}
        default List<PersonalRounds.Wager> lastWagers(UUID player) { return List.of(); }
    }

    public record Seat(String id, String name, int seat, long stack, long bet, List<Integer> cards,
                       boolean folded, boolean active, boolean leaving, String result) {}
    public record Bet(String id, String owner, String kind, int number, long amount) {}
    public record View(String kind, String phase, long round, long revision, long remainingMillis,
                       long startedMillis, long deadlineMillis, long changedMillis,
                       List<Seat> seats, List<Integer> board, List<Integer> dealer, long pot,
                       int button, int result, List<Integer> segments, List<Bet> bets,
                       List<String> actions, long call, long minimumRaise, String message,
                       long minBet, long maxBet, long buyInMin, long buyInMax) {}

    protected final Bank bank;
    protected Rules rules;
    protected long round = 1;
    protected long revision;
    protected long deadline;
    protected long startedAt;
    protected long changedAt;
    protected String phase = "betting";
    protected String message = "";

    protected Game(Bank bank, Rules rules) { this.bank = bank; this.rules = rules; }
    public abstract String kind();
    public abstract void action(UUID player, String action, long amount, int option, long now);
    public abstract void timeout(long now);
    public abstract void depart(UUID player, long now);
    public abstract void close();
    public abstract boolean contains(UUID player);
    public abstract boolean funded();
    public abstract List<UUID> players();
    public abstract View view(UUID viewer, long now);
    public long revision() { return revision; }
    public long round() { return round; }
    public long deadline() { return deadline; }
    public Rules rules() { return rules; }
    public void nextRules(Rules next) { if (!funded() && phase.equals("betting")) rules = next; }

    public void guarded(UUID player, long expectedRound, long expectedRevision, String action, long amount, int option, long now) {
        if (deadline > 0 && now >= deadline) timeout(now);
        if (round == expectedRound && (action.equals("bet") || action.equals("repeat") || action.startsWith("roulette_"))
                && !phase.equals("betting")) throw new IllegalArgumentException("casino.error.betting_closed");
        if (round != expectedRound || revision != expectedRevision) throw new IllegalArgumentException("casino.error.stale");
        Money.checked(amount);
        action(player, action, amount, option, now);
        changedAt = now;
        revision++;
    }

    protected void require(boolean condition) { if (!condition) throw new IllegalArgumentException("casino.error.action"); }
    protected void require(boolean condition, String error) { if (!condition) throw new IllegalArgumentException(error); }
    protected void debit(UUID player, long amount) {
        if (!bank.available(player)) throw new IllegalArgumentException("casino.error.busy");
        if (!bank.debit(player, amount)) throw new IllegalArgumentException("casino.error.chips");
    }
    protected long remaining(long now) { return Math.max(0, deadline - now); }
    protected View view(List<Seat> seats, List<Integer> board, List<Integer> dealer, long pot,
                        int button, int result, List<Integer> segments, List<Bet> bets, List<String> actions,
                        long call, long minimumRaise, long now) {
        return new View(kind(), phase, round, revision, 0, startedAt, deadline, changedAt, seats, board, dealer, pot,
                button, result, segments, bets, actions, call, minimumRaise, message,
                rules.minBet(), rules.maxBet(), rules.buyInMin(), rules.buyInMax());
    }
}
