package de.shiina.casino.logic;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Cards {
    public record Card(int rank, int suit) {
        public Card {
            if (rank < 2 || rank > 14 || suit < 0 || suit > 3) throw new IllegalArgumentException("card");
        }
        public int code() { return suit * 13 + rank - 2; }
        public int blackjack() { return rank == 14 ? 11 : Math.min(rank, 10); }
    }

    public static final class Deck {
        private final List<Card> cards = new ArrayList<>();
        private final SecureRandom random;
        private int offset;

        public Deck(int decks, SecureRandom random) { this.random = random; shuffle(decks); }
        public Deck(List<Card> ordered) {
            random = new SecureRandom(); cards.addAll(ordered);
        }
        public void shuffle(int decks) {
            cards.clear(); offset = 0;
            for (int deck = 0; deck < decks; deck++)
                for (int suit = 0; suit < 4; suit++)
                    for (int rank = 2; rank <= 14; rank++) cards.add(new Card(rank, suit));
            Collections.shuffle(cards, random);
        }
        public Card draw() {
            if (offset >= cards.size()) throw new IllegalStateException("shoe exhausted");
            return cards.get(offset++);
        }
        public int remaining() { return cards.size() - offset; }
    }

    private Cards() {}
    public static int blackjack(List<Card> hand) {
        int total = hand.stream().mapToInt(Card::blackjack).sum();
        long aces = hand.stream().filter(card -> card.rank == 14).count();
        while (total > 21 && aces-- > 0) total -= 10;
        return total;
    }
    public static boolean natural(List<Card> hand) { return hand.size() == 2 && blackjack(hand) == 21; }
    public static List<Integer> codes(List<Card> hand) { return hand.stream().map(Card::code).toList(); }
}
