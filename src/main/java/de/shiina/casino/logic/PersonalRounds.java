package de.shiina.casino.logic;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Small session-only comfort data. Contains no cards and never moves money. */
public final class PersonalRounds {
    public record Result(String game, UUID transaction, long round, long stake, long payout) {
        public Result { Money.checked(stake); Money.checked(payout); }
        public long net() { return payout - stake; }
    }
    public record Wager(Roulette.Kind kind, int number, long amount) {
        public Wager { Roulette.validate(kind, number); Money.checked(amount); }
    }
    private static final class Session {
        final ArrayDeque<Result> history = new ArrayDeque<>(10);
        long blackjack, wheel;
        List<Wager> wagers = List.of();
    }
    private final Map<UUID, Session> sessions = new HashMap<>();

    public void open(UUID player) { sessions.computeIfAbsent(player, ignored -> new Session()); }
    public void forget(UUID player) { sessions.remove(player); }
    public void clear() { sessions.clear(); }
    public int size() { return sessions.size(); }
    public List<Result> history(UUID player) {
        Session session = sessions.get(player);
        return session == null ? List.of() : List.copyOf(session.history);
    }
    public void complete(UUID player, Result result) {
        Session session = sessions.get(player);
        if (session == null || session.history.stream().anyMatch(old -> old.transaction().equals(result.transaction()) && old.round() == result.round())) return;
        if (session.history.size() == 10) session.history.removeLast();
        session.history.addFirst(result);
    }
    public void remember(UUID player, String game, long amount) {
        Money.checked(amount);
        Session session = sessions.get(player);
        if (session == null) return;
        if (game.equals("blackjack")) session.blackjack = amount;
        if (game.equals("wheel")) session.wheel = amount;
    }
    public long lastBet(UUID player, String game) {
        Session session = sessions.get(player);
        return session == null ? 0 : game.equals("blackjack") ? session.blackjack : game.equals("wheel") ? session.wheel : 0;
    }
    public void rememberWagers(UUID player, List<Wager> wagers) {
        if (wagers.isEmpty() || wagers.size() > 12) throw new IllegalArgumentException("casino.error.template");
        Session session = sessions.get(player);
        if (session != null) session.wagers = List.copyOf(wagers);
    }
    public List<Wager> lastWagers(UUID player) {
        Session session = sessions.get(player);
        return session == null ? List.of() : session.wagers;
    }
}
