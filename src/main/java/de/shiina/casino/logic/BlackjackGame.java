package de.shiina.casino.logic;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BlackjackGame extends Game {
    private static final class Hand {
        final UUID player;
        final int seat;
        final List<Cards.Card> cards = new ArrayList<>();
        long bet;
        boolean stood;
        String result = "";
        Hand(UUID player, int seat, long bet) { this.player = player; this.seat = seat; this.bet = bet; }
    }

    private final Map<UUID, Hand> hands = new LinkedHashMap<>();
    private final List<Cards.Card> dealer = new ArrayList<>();
    private final Cards.Deck shoe;
    private UUID transaction = UUID.randomUUID();
    private UUID turn;
    private boolean paid;
    private boolean dealerRevealed;

    public BlackjackGame(Bank bank, Rules rules, SecureRandom random) {
        this(bank, rules, new Cards.Deck(6, random));
    }

    public BlackjackGame(Bank bank, Rules rules, Cards.Deck shoe) { super(bank, rules); this.shoe = shoe; }

    public static long payout(long bet, List<Cards.Card> player, List<Cards.Card> dealer) {
        Money.checked(bet);
        if (bet % 2 != 0) throw new IllegalArgumentException("casino.error.even");
        int value = Cards.blackjack(player), house = Cards.blackjack(dealer);
        if (value > 21) return 0;
        if (Cards.natural(dealer)) return Cards.natural(player) ? bet : 0;
        if (Cards.natural(player)) return Money.add(bet, Money.multiply(bet / 2, 3));
        if (house > 21 || value > house) return Money.multiply(bet, 2);
        return value == house ? bet : 0;
    }

    @Override public String kind() { return "blackjack"; }
    @Override public boolean contains(UUID player) { return hands.containsKey(player); }
    @Override public boolean funded() { return !hands.isEmpty() && !paid; }
    @Override public List<UUID> players() { return List.copyOf(hands.keySet()); }

    private void journal() {
        Map<UUID, Long> amounts = new LinkedHashMap<>();
        hands.forEach((player, hand) -> amounts.put(player, hand.bet));
        bank.hold(transaction, kind(), round, amounts);
    }

    @Override public void action(UUID player, String action, long amount, int option, long now) {
        if (action.equals("bet")) {
            require(rules.blackjack() && phase.equals("betting") && !contains(player) && hands.size() < 6);
            rules.bet(amount, true);
            int seat = bank.seat(player);
            if (seat >= 0) require(seat < 6 && !hasSeat(seat));
            else { seat = 0; while (hasSeat(seat)) seat++; }
            debit(player, amount);
            hands.put(player, new Hand(player, seat, amount));
            journal();
            if (deadline == 0) { startedAt = now; deadline = now + rules.bettingSeconds() * 1000L; }
            return;
        }
        if (action.equals("leave") && phase.equals("betting")) { depart(player, now); return; }
        require(phase.equals("playing") && player.equals(turn));
        Hand hand = hands.get(player);
        require(hand != null && !hand.stood);
        switch (action) {
            case "hit" -> { hand.cards.add(shoe.draw()); if (Cards.blackjack(hand.cards) >= 21) hand.stood = true; }
            case "stand" -> hand.stood = true;
            case "double" -> {
                require(hand.cards.size() == 2);
                debit(player, hand.bet);
                hand.bet = Money.multiply(hand.bet, 2);
                journal();
                hand.cards.add(shoe.draw()); hand.stood = true;
            }
            default -> throw new IllegalArgumentException("casino.error.action");
        }
        if (hand.stood) advance(now); else deadline = now + rules.turnSeconds() * 1000L;
    }

    private boolean hasSeat(int seat) { return hands.values().stream().anyMatch(hand -> hand.seat == seat); }

    private void deal(long now) {
        if (hands.isEmpty()) { deadline = 0; return; }
        if (shoe.remaining() < 156) shoe.shuffle(6);
        for (int pass = 0; pass < 2; pass++) {
            hands.values().forEach(hand -> hand.cards.add(shoe.draw()));
            dealer.add(shoe.draw());
        }
        phase = "playing"; startedAt = now;
        if (Cards.natural(dealer)) { beginDealer(now); return; }
        hands.values().forEach(hand -> { if (Cards.natural(hand.cards)) hand.stood = true; });
        advance(now);
    }

    private void advance(long now) {
        turn = hands.values().stream().filter(hand -> !hand.stood).map(hand -> hand.player).findFirst().orElse(null);
        if (turn != null) { deadline = now + rules.turnSeconds() * 1000L; return; }
        beginDealer(now);
    }

    private void beginDealer(long now) {
        turn = null; phase = "dealer_reveal"; startedAt = now; deadline = now + 1000;
    }

    private void dealerNext(long now) {
        phase = Cards.blackjack(dealer) < 17 ? "dealer_draw" : "dealer_wait";
        startedAt = now; deadline = now + (phase.equals("dealer_draw") ? 1200 : 1500);
    }

    private void settle(long now) {
        if (paid) return;
        Map<UUID, Long> awards = new LinkedHashMap<>();
        hands.forEach((player, hand) -> {
            long award = payout(hand.bet, hand.cards, dealer);
            awards.put(player, award);
            hand.result = award == 0 ? "loss" : award == hand.bet ? "push" : "win";
        });
        bank.hold(transaction, kind(), round, awards);
        bank.settle(transaction);
        paid = true; turn = null; phase = "result"; startedAt = now; deadline = now + 6000;
    }

    @Override public void timeout(long now) {
        if (deadline == 0 || now < deadline) return;
        switch (phase) {
            case "betting" -> deal(now);
            case "playing" -> { hands.get(turn).stood = true; advance(now); }
            case "dealer_reveal" -> { dealerRevealed = true; dealerNext(now); }
            case "dealer_draw" -> { dealer.add(shoe.draw()); dealerNext(now); }
            case "dealer_wait" -> settle(now);
            case "result" -> {
                hands.clear(); dealer.clear(); paid = false; dealerRevealed = false; phase = "betting";
                round++; transaction = UUID.randomUUID(); deadline = 0;
            }
            default -> throw new IllegalStateException(phase);
        }
        changedAt = now; revision++;
    }

    @Override public void depart(UUID player, long now) {
        Hand hand = hands.get(player);
        if (hand == null) return;
        if (phase.equals("betting")) {
            hands.remove(player); journal(); bank.credit(player, hand.bet);
            if (hands.isEmpty()) { bank.settle(transaction); deadline = 0; }
        } else if (phase.equals("playing") && !hand.stood) {
            hand.stood = true;
            if (player.equals(turn)) advance(now);
        } else return;
        changedAt = now; revision++;
    }

    @Override public void close() {
        if (!paid && !hands.isEmpty()) bank.settle(transaction);
        hands.clear(); deadline = 0;
    }

    @Override public View view(UUID viewer, long now) {
        List<Seat> seats = hands.values().stream().map(hand -> new Seat(hand.player.toString(), bank.name(hand.player),
                hand.seat, 0, hand.bet, Cards.codes(hand.cards), Cards.blackjack(hand.cards) > 21,
                hand.player.equals(turn), false, hand.result)).toList();
        List<Integer> house = new ArrayList<>();
        if (!dealer.isEmpty()) {
            house.add(dealer.getFirst().code());
            if (dealerRevealed) house = Cards.codes(dealer); else house.add(-1);
        }
        List<String> actions = new ArrayList<>();
        if (rules.blackjack() && phase.equals("betting") && !contains(viewer) && hands.size() < 6) actions.add("bet");
        if (phase.equals("betting") && contains(viewer)) actions.add("leave");
        if (viewer.equals(turn)) {
            actions.add("hit"); actions.add("stand");
            if (hands.get(viewer).cards.size() == 2) actions.add("double");
        }
        return view(seats, List.of(), house, 0, -1, -1, List.of(), List.of(), actions, 0, 0, now);
    }
}
