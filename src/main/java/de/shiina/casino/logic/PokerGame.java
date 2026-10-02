package de.shiina.casino.logic;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PokerGame extends Game {
    private static final class Player {
        final UUID id;
        final int seat;
        final List<Cards.Card> cards = new ArrayList<>(2);
        long stack, street, contribution, lastActionBet;
        boolean folded, acted, inHand, leaving;
        String result = "";
        Player(UUID id, int seat, long stack) { this.id = id; this.seat = seat; this.stack = stack; }
    }

    private final Player[] seats = new Player[6];
    private final List<Cards.Card> board = new ArrayList<>(5);
    private final SecureRandom random;
    private boolean refreshRules;
    private Cards.Deck deck;
    private UUID transaction = UUID.randomUUID();
    private int button = -1, turn = -1, lastBigBlind = -1;
    private long currentBet, minimumRaise;
    private boolean showdown;
    private long lastPot;

    public PokerGame(Bank bank, Rules rules, SecureRandom random) { super(bank, rules); this.random = random; phase = "lobby"; }
    @Override public String kind() { return "poker"; }
    @Override public List<UUID> players() { return occupied().stream().map(player -> player.id).toList(); }
    @Override public boolean contains(UUID player) { return occupied().stream().anyMatch(seat -> seat.id.equals(player)); }
    @Override public boolean funded() { return !occupied().isEmpty(); }
    public long totalChips() { return occupied().stream().mapToLong(player -> player.stack + player.contribution).sum(); }
    public int turnSeat() { return turn; }
    public int buttonSeat() { return button; }
    public long minimumRaiseTo() { return Money.add(currentBet, minimumRaise); }
    @Override public void nextRules(Rules next) {
        if (phase.equals("lobby") && (occupied().isEmpty() || refreshRules)) { rules = next; refreshRules = false; }
    }

    private List<Player> occupied() {
        List<Player> players = new ArrayList<>(6);
        for (Player seat : seats) if (seat != null) players.add(seat);
        return players;
    }
    private List<Player> live() { return occupied().stream().filter(player -> player.inHand && !player.folded).toList(); }
    private boolean playing() { return !phase.equals("lobby") && !phase.equals("result"); }
    private boolean actionable(Player player) { return player != null && player.inHand && !player.folded && player.stack > 0; }
    private boolean canRaise(Player player) { return !player.acted || currentBet - player.lastActionBet >= minimumRaise; }
    private Player player(UUID id) { return occupied().stream().filter(seat -> seat.id.equals(id)).findFirst().orElse(null); }
    private void journal() {
        Map<UUID, Long> amounts = new LinkedHashMap<>();
        occupied().forEach(player -> amounts.put(player.id, Money.add(player.stack, player.contribution)));
        bank.hold(transaction, kind(), round, amounts);
    }

    @Override public void action(UUID id, String action, long amount, int option, long now) {
        if (action.equals("join")) {
            require(rules.poker() && phase.equals("lobby") && !contains(id) && occupied().size() < 6);
            require(amount >= rules.buyInMin() && amount <= rules.buyInMax());
            int seat = bank.seat(id);
            if (seat >= 0) require(seat < seats.length && seats[seat] == null);
            else { seat = 0; while (seats[seat] != null) seat++; }
            debit(id, amount);
            seats[seat] = new Player(id, seat, amount); journal();
            if (occupied().size() >= 2 && deadline == 0) { startedAt = now; deadline = now + rules.bettingSeconds() * 1000L; }
            return;
        }
        Player player = player(id); require(player != null);
        if (action.equals("leave")) { depart(id, now); return; }
        require(playing() && turn == player.seat && actionable(player));
        act(player, action, amount);
        progress(player.seat, now);
        journal();
    }

    private void pay(Player player, long amount) {
        require(amount >= 0 && amount <= player.stack);
        player.stack -= amount;
        player.street = Money.add(player.street, amount);
        player.contribution = Money.add(player.contribution, amount);
    }

    private void act(Player player, String action, long amount) {
        long owed = currentBet - player.street;
        switch (action) {
            case "fold" -> player.folded = true;
            case "check" -> require(owed == 0);
            case "call" -> { require(owed > 0); pay(player, Math.min(owed, player.stack)); }
            case "raise", "allin" -> {
                long target = action.equals("allin") ? Money.add(player.street, player.stack) : amount;
                require(target > player.street && target <= Money.add(player.street, player.stack));
                if (target > currentBet) {
                    require(canRaise(player));
                    long increment = target - currentBet;
                    boolean full = increment >= minimumRaise;
                    require(full || target == player.street + player.stack);
                    if (full) {
                        minimumRaise = increment;
                        occupied().forEach(other -> { if (other != player) other.acted = false; });
                    }
                    currentBet = target;
                } else require(action.equals("allin"));
                pay(player, target - player.street);
            }
            default -> throw new IllegalArgumentException("casino.error.action");
        }
        player.acted = true; player.lastActionBet = currentBet;
    }

    private int nextSeat(int after, java.util.function.Predicate<Player> predicate) {
        for (int offset = 1; offset <= 6; offset++) {
            int candidate = Math.floorMod(after + offset, 6);
            if (seats[candidate] != null && predicate.test(seats[candidate])) return candidate;
        }
        return -1;
    }

    private void start(long now) {
        if (!rules.poker()) { for (Player player : List.copyOf(occupied())) cashOut(player); deadline = 0; return; }
        if (occupied().size() < 2) { deadline = 0; return; }
        for (Player player : occupied()) {
            player.cards.clear(); player.inHand = true; player.folded = false;
            player.acted = false; player.street = 0; player.contribution = 0; player.lastActionBet = 0; player.result = "";
        }
        board.clear(); showdown = false; lastPot = 0;
        deck = new Cards.Deck(1, random);
        button = nextSeat(button, player -> player.stack > 0);
        if (occupied().size() == 2 && lastBigBlind >= 0 && seats[lastBigBlind] != null && seats[lastBigBlind].stack > 0) button = lastBigBlind;
        int small = occupied().size() == 2 ? button : nextSeat(button, player -> player.stack > 0);
        int big = nextSeat(small, player -> player.stack > 0);
        lastBigBlind = big;
        for (int pass = 0; pass < 2; pass++) {
            int cursor = button;
            for (int count = 0; count < occupied().size(); count++) {
                cursor = nextSeat(cursor, player -> player.inHand); seats[cursor].cards.add(deck.draw());
            }
        }
        pay(seats[small], Math.min(rules.smallBlind(), seats[small].stack));
        pay(seats[big], Math.min(rules.bigBlind(), seats[big].stack));
        currentBet = rules.bigBlind(); minimumRaise = rules.bigBlind(); phase = "preflop";
        progress(big, now); journal();
    }

    private void progress(int after, long now) {
        while (playing()) {
            if (live().size() == 1) { finish(false, now); return; }
            List<Player> able = live().stream().filter(this::actionable).toList();
            boolean mustRespond = able.stream().anyMatch(player -> player.street < currentBet);
            if (able.size() < 2 && !mustRespond) { runout(); finish(true, now); return; }
            int next = nextSeat(after, player -> actionable(player) && (!player.acted || player.street < currentBet));
            if (next == -1) {
                if (phase.equals("river")) { finish(true, now); return; }
                advanceStreet(); after = button; continue;
            }
            turn = next;
            if (seats[next].leaving) {
                act(seats[next], seats[next].street == currentBet ? "check" : "fold", 0);
                after = next; continue;
            }
            startedAt = now; deadline = now + rules.turnSeconds() * 1000L;
            return;
        }
    }

    private void advanceStreet() {
        deck.draw();
        if (board.isEmpty()) { for (int count = 0; count < 3; count++) board.add(deck.draw()); phase = "flop"; }
        else { board.add(deck.draw()); phase = board.size() == 4 ? "turn" : "river"; }
        occupied().forEach(player -> { player.street = 0; player.acted = false; player.lastActionBet = 0; });
        currentBet = 0; minimumRaise = rules.bigBlind();
    }

    private void runout() { while (board.size() < 5) advanceStreet(); }

    private void finish(boolean reveal, long now) {
        showdown = reveal; lastPot = occupied().stream().mapToLong(player -> player.contribution).sum();
        List<PokerHands.Contender> contenders = new ArrayList<>();
        for (Player player : occupied()) {
            long score = 0;
            if (reveal && !player.folded) {
                List<Cards.Card> seven = new ArrayList<>(board); seven.addAll(player.cards);
                score = PokerHands.evaluate(seven);
            }
            contenders.add(new PokerHands.Contender(player.id, player.seat, player.contribution, player.folded, score));
        }
        long before = totalChips();
        Map<UUID, Long> awards = PokerHands.distribute(contenders, button);
        for (Player player : occupied()) {
            long award = awards.get(player.id);
            player.stack = Money.add(player.stack, award); player.contribution = 0; player.street = 0;
            player.result = award > 0 ? "win" : "loss";
        }
        if (totalChips() != before) throw new IllegalStateException("chip conservation");
        phase = "result"; turn = -1; startedAt = now; deadline = now + 7000; journal();
        for (Player player : List.copyOf(occupied())) if (player.leaving || player.stack == 0) cashOut(player);
    }

    private void cashOut(Player player) {
        long amount = Money.add(player.stack, player.contribution);
        seats[player.seat] = null; journal(); bank.credit(player.id, amount);
        if (occupied().isEmpty()) { bank.settle(transaction); transaction = UUID.randomUUID(); }
    }

    @Override public void timeout(long now) {
        if (deadline == 0 || now < deadline) return;
        if (phase.equals("lobby")) start(now);
        else if (phase.equals("result")) {
            round++; phase = "lobby"; board.clear(); refreshRules = true;
            occupied().forEach(player -> { player.cards.clear(); player.inHand = false; player.result = ""; });
            deadline = occupied().size() >= 2 ? now + rules.bettingSeconds() * 1000L : 0;
        } else {
            Player player = seats[turn]; act(player, player.street == currentBet ? "check" : "fold", 0);
            progress(player.seat, now); journal();
        }
        changedAt = now; revision++;
    }

    @Override public void depart(UUID id, long now) {
        Player player = player(id); if (player == null) return;
        if (!playing()) {
            cashOut(player);
            if (phase.equals("lobby") && occupied().size() < 2) deadline = 0;
        } else {
            if (player.leaving) return;
            player.leaving = true;
            if (turn == player.seat) {
                act(player, player.street == currentBet ? "check" : "fold", 0); progress(player.seat, now); journal();
            }
        }
        changedAt = now; revision++;
    }

    @Override public void close() {
        bank.settle(transaction);
        java.util.Arrays.fill(seats, null); deadline = 0;
    }

    @Override public View view(UUID viewer, long now) {
        List<Seat> visible = occupied().stream().map(player -> {
            boolean reveal = player.id.equals(viewer);
            List<Integer> cards = player.cards.isEmpty() ? List.of() : reveal ? Cards.codes(player.cards) : List.of(-1, -1);
            return new Seat(player.id.toString(), bank.name(player.id), player.seat, player.stack, player.street,
                    cards, player.folded, player.seat == turn, player.leaving, player.result);
        }).toList();
        List<String> actions = new ArrayList<>();
        Player player = player(viewer);
        long call = 0;
        if (rules.poker() && player == null && phase.equals("lobby") && occupied().size() < 6) actions.add("join");
        if (player != null && !player.leaving) actions.add("leave");
        if (player != null && player.seat == turn && playing()) {
            call = Math.min(player.stack, currentBet - player.street);
            actions.add("fold"); actions.add(call == 0 ? "check" : "call");
            if (canRaise(player) && player.stack + player.street > currentBet) actions.add("raise");
            if (player.stack + player.street <= currentBet || canRaise(player)) actions.add("allin");
        }
        long pot = phase.equals("result") ? lastPot : occupied().stream().mapToLong(seat -> seat.contribution).sum();
        return view(visible, Cards.codes(board), List.of(), pot, button, -1, List.of(), List.of(), actions,
                call, minimumRaiseTo(), now);
    }
}
