package de.shiina.casino.logic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PokerHands {
    public record Contender(UUID player, int seat, long contribution, boolean folded, long score) {}
    private PokerHands() {}

    public static long evaluate(List<Cards.Card> cards) {
        if (cards.size() < 5 || cards.size() > 7) throw new IllegalArgumentException("hand length");
        if (cards.stream().distinct().count() != cards.size()) throw new IllegalArgumentException("duplicate card");
        long best = 0;
        for (int first = 0; first < cards.size() - 4; first++)
            for (int second = first + 1; second < cards.size() - 3; second++)
                for (int third = second + 1; third < cards.size() - 2; third++)
                    for (int fourth = third + 1; fourth < cards.size() - 1; fourth++)
                        for (int fifth = fourth + 1; fifth < cards.size(); fifth++)
                            best = Math.max(best, five(List.of(cards.get(first), cards.get(second), cards.get(third), cards.get(fourth), cards.get(fifth))));
        return best;
    }

    public static int category(long score) { return (int) (score / 759375L); }

    private static long encode(int category, List<Integer> ranks) {
        long score = category;
        for (int index = 0; index < 5; index++) score = score * 15 + (index < ranks.size() ? ranks.get(index) : 0);
        return score;
    }

    private static long five(List<Cards.Card> hand) {
        Map<Integer, Integer> counts = new HashMap<>();
        hand.forEach(card -> counts.merge(card.rank(), 1, Integer::sum));
        List<Integer> ranks = counts.keySet().stream().sorted(Comparator.reverseOrder()).toList();
        boolean flush = hand.stream().allMatch(card -> card.suit() == hand.getFirst().suit());
        int straight = 0;
        if (ranks.size() == 5) {
            if (ranks.getFirst() - ranks.getLast() == 4) straight = ranks.getFirst();
            else if (ranks.equals(List.of(14, 5, 4, 3, 2))) straight = 5;
        }
        if (flush && straight > 0) return encode(8, List.of(straight));
        List<Integer> grouped = new ArrayList<>(ranks);
        grouped.sort(Comparator.<Integer>comparingInt(counts::get).reversed().thenComparing(Comparator.reverseOrder()));
        int largest = counts.get(grouped.getFirst());
        if (largest == 4) return encode(7, grouped);
        if (largest == 3 && counts.get(grouped.get(1)) == 2) return encode(6, grouped);
        if (flush) return encode(5, ranks);
        if (straight > 0) return encode(4, List.of(straight));
        if (largest == 3) return encode(3, grouped);
        if (largest == 2 && counts.get(grouped.get(1)) == 2) return encode(2, grouped);
        if (largest == 2) return encode(1, grouped);
        return encode(0, ranks);
    }

    public static Map<UUID, Long> distribute(List<Contender> contenders, int button) {
        Map<UUID, Long> awards = new LinkedHashMap<>();
        contenders.forEach(player -> { Money.checked(player.contribution); awards.put(player.player, 0L); });
        List<Long> levels = contenders.stream().map(Contender::contribution).filter(amount -> amount > 0).distinct().sorted().toList();
        long previous = 0;
        for (long level : levels) {
            List<Contender> contributors = contenders.stream().filter(player -> player.contribution >= level).toList();
            long pot = Money.multiply(level - previous, contributors.size()); previous = level;
            if (contributors.size() == 1) {
                awards.merge(contributors.getFirst().player, pot, Money::add);
                continue;
            }
            List<Contender> eligible = contributors.stream().filter(player -> !player.folded).toList();
            if (eligible.isEmpty()) throw new IllegalStateException("pot without eligible player");
            long best = eligible.stream().mapToLong(Contender::score).max().orElseThrow();
            List<Contender> winners = eligible.stream().filter(player -> player.score == best)
                    .sorted(Comparator.comparingInt(player -> Math.floorMod(player.seat - button - 1, 6))).toList();
            long share = pot / winners.size(), odd = pot % winners.size();
            for (int index = 0; index < winners.size(); index++)
                awards.merge(winners.get(index).player, share + (index < odd ? 1 : 0), Money::add);
        }
        long contributed = contenders.stream().mapToLong(Contender::contribution).sum();
        long awarded = awards.values().stream().mapToLong(Long::longValue).sum();
        if (contributed != awarded) throw new IllegalStateException("chip conservation");
        return awards;
    }
}
