package de.shiina.casino.logic;

import static org.junit.jupiter.api.Assertions.*;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BlackjackTest {
    private List<Cards.Card> hand(int... ranks) {
        List<Cards.Card> result = new ArrayList<>();
        for (int rank : ranks) result.add(new Cards.Card(rank, 0));
        return result;
    }
    private BlackjackGame game(TestBank bank, int... ranks) {
        List<Cards.Card> cards = new ArrayList<>(hand(ranks));
        while (cards.size() < 312) cards.add(new Cards.Card(10, 0));
        return new BlackjackGame(bank, Rules.defaults(), new Cards.Deck(cards));
    }
    private void bet(BlackjackGame game, UUID player, long amount) { game.guarded(player, game.round(), game.revision(), "bet", amount, 0, 0); }

    @Test void acesAdaptAndSoft17Stands() {
        assertEquals(21, Cards.blackjack(hand(14,14,9)));
        assertEquals(17, Cards.blackjack(hand(14,6)));
        assertEquals(17, Cards.blackjack(hand(14,6,10)));
        TestBank bank = new TestBank(); UUID player = bank.player(100);
        BlackjackGame game = game(bank, 9,14,9,6,10);
        bet(game, player, 20); game.timeout(20000);
        game.guarded(player, game.round(), game.revision(), "stand", 0, 0, 21000);
        assertEquals(2, game.view(player, 21000).dealer().size());
        game.timeout(22000); game.timeout(23500);
        assertEquals(40, bank.ledger.balance(player));
    }
    @Test void naturalPaysThreeToTwoAndBeatsOrdinary21() {
        assertEquals(50, BlackjackGame.payout(20, hand(14,13), hand(7,7,7)));
        assertEquals(20, BlackjackGame.payout(20, hand(14,13), hand(14,10)));
        assertEquals(0, BlackjackGame.payout(20, hand(10,6,5), hand(14,10)));
        assertEquals(20, BlackjackGame.payout(20, hand(10,10), hand(10,10)));
        assertEquals(0, BlackjackGame.payout(20, hand(10,10,2), hand(10,6,10)));
        assertEquals(40, BlackjackGame.payout(20, hand(10,6,5), hand(10,9)));
        assertThrows(IllegalArgumentException.class, () -> BlackjackGame.payout(3, hand(14,10), hand(10,7)));
    }
    @Test void dealerBlackjackSettlesBeforeAnyDouble() {
        TestBank bank = new TestBank(); UUID player = bank.player(100);
        BlackjackGame game = game(bank, 9,14,10,13);
        bet(game, player, 20); game.timeout(20000);
        assertEquals("dealer_reveal", game.view(player, 20000).phase());
        game.timeout(21000); game.timeout(22500);
        assertEquals("result", game.view(player, 22500).phase());
        assertEquals(0, bank.ledger.balance(player));
        assertThrows(IllegalArgumentException.class, () -> game.guarded(player, game.round(), game.revision(), "double", 20, 0, 20001));
        assertEquals(80, bank.inventory.get(player));
    }
    @Test void dealerHoleCardNeverAppearsBeforeReveal() {
        TestBank bank = new TestBank(); UUID player = bank.player(100);
        BlackjackGame game = game(bank, 9,10,8,6);
        bet(game, player, 20); game.timeout(20000);
        assertEquals(List.of(8,-1), game.view(UUID.randomUUID(), 20000).dealer());
        assertEquals(2, game.view(UUID.randomUUID(), 20000).seats().getFirst().cards().size());
    }
    @Test void doublingConsumesExactlyOneExtraStakeAndDrawsOnce() {
        TestBank bank = new TestBank(); UUID player = bank.player(100);
        BlackjackGame game = game(bank, 8,10,3,6,10,10);
        bet(game, player, 20); game.timeout(20000);
        long revision = game.revision();
        game.guarded(player, game.round(), revision, "double", 0, 0, 21000);
        assertEquals(40, bank.debits.get(player)); assertEquals(60, bank.inventory.get(player));
        game.timeout(22000); game.timeout(23200); game.timeout(24700);
        assertEquals(80, bank.ledger.balance(player));
        assertEquals(3, game.view(player, 21000).seats().getFirst().cards().size());
        assertThrows(IllegalArgumentException.class, () -> game.guarded(player, game.round(), revision, "double", 0, 0, 21001));
        assertEquals(80, bank.ledger.balance(player));
    }
    @Test void invalidDoubleCannotConsumeChips() {
        TestBank bank = new TestBank(); UUID player = bank.player(20);
        BlackjackGame game = game(bank, 8,10,3,6);
        bet(game, player, 20); game.timeout(20000);
        assertThrows(IllegalArgumentException.class, () -> game.guarded(player, game.round(), game.revision(), "double", 0, 0, 21000));
        assertEquals(0, bank.inventory.get(player));
        assertEquals(20, bank.ledger.pending().values().iterator().next().recovery().get(player));
    }
    @Test void oddStakeAndSeventhSeatAreRejected() {
        TestBank bank = new TestBank(); BlackjackGame game = new BlackjackGame(bank, Rules.defaults(), new SecureRandom());
        UUID player = bank.player(100);
        assertThrows(IllegalArgumentException.class, () -> bet(game, player, 3));
        for (int seat = 0; seat < 6; seat++) bet(game, bank.player(100), 20);
        assertThrows(IllegalArgumentException.class, () -> bet(game, player, 20));
        assertEquals(100, bank.inventory.get(player));
    }
    @Test void timeoutStandsAndDepartureBeforeDealRefundsOnce() {
        TestBank bank = new TestBank(); UUID player = bank.player(100);
        BlackjackGame game = game(bank, 9,10,9,7);
        assertEquals(0, game.deadline()); bet(game, player, 20);
        game.depart(player, 1000); game.depart(player, 1001);
        assertEquals(20, bank.ledger.balance(player)); assertEquals(0, game.deadline());
        assertTrue(bank.ledger.pending().isEmpty());
        bet(game, player, 20); game.timeout(20000); game.timeout(50000);
        assertEquals("dealer_reveal", game.view(player, 50000).phase());
        game.timeout(51000); game.timeout(52500);
        assertEquals("result", game.view(player, 52500).phase());
    }
    @Test void restartOrUnloadRefundsDoubledUnfinishedHand() {
        TestBank bank = new TestBank(); UUID first = bank.player(100), second = bank.player(100);
        BlackjackGame game = game(bank, 8,7,10,3,8,6,10);
        bet(game, first, 20); bet(game, second, 20); game.timeout(20000);
        game.guarded(first, game.round(), game.revision(), "double", 0, 0, 21000);
        assertEquals("playing", game.view(first, 21000).phase());
        bank.ledger.recoverAll(); bank.ledger.recoverAll();
        assertEquals(40, bank.ledger.balance(first)); assertEquals(20, bank.ledger.balance(second));
    }
    @Test void disconnectAfterDealStandsInsteadOfRefundingLoss() {
        TestBank bank = new TestBank(); UUID player = bank.player(100);
        BlackjackGame game = game(bank, 8,10,8,10); bet(game, player, 20); game.timeout(20000);
        game.depart(player, 21000); game.close();
        assertEquals(20, bank.ledger.balance(player));
    }
    @Test void dealerStepsAreSharedPrivateTimedAndPayExactlyOnceDespiteDepartureAndReopen() {
        TestBank bank = new TestBank(); UUID player = bank.player(100), observer = UUID.randomUUID();
        BlackjackGame game = game(bank, 10,2,9,3,4,8);
        bet(game, player, 20); game.timeout(20000);
        game.guarded(player, game.round(), game.revision(), "stand", 0, 0, 21000);
        assertEquals("dealer_reveal", game.view(player,21000).phase());
        assertEquals(List.of(0,-1),game.view(observer,21000).dealer());
        game.timeout(21999); assertTrue(game.view(player,21999).dealer().contains(-1));
        game.depart(player,21999);
        assertThrows(IllegalArgumentException.class, () -> game.action(player,"hit",0,0,21999));
        game.timeout(22000);
        assertEquals(List.of(0,1),game.view(observer,22000).dealer());
        assertEquals(game.view(player,22000).dealer(),game.view(observer,22000).dealer());
        long deadline=game.deadline(); game.view(player,22500); assertEquals(deadline,game.deadline());
        game.timeout(23199); assertEquals(2,game.view(observer,23199).dealer().size());
        game.timeout(23200); assertEquals(3,game.view(observer,23200).dealer().size());
        game.timeout(24400); assertEquals(4,game.view(observer,24400).dealer().size());
        assertEquals("dealer_wait",game.view(observer,24400).phase());
        assertEquals(0,bank.ledger.balance(player));
        game.timeout(25899); assertEquals(0,bank.ledger.balance(player));
        game.timeout(25900); assertEquals(40,bank.ledger.balance(player));
        game.timeout(25900); game.depart(player,26000); game.close(); game.close();
        assertEquals(40,bank.ledger.balance(player)); assertTrue(bank.ledger.pending().isEmpty());
    }
    @Test void shoeContainsSixCopiesAndNeverRedrawsBeyondCapacity() {
        Cards.Deck deck = new Cards.Deck(6, new SecureRandom());
        java.util.Map<Cards.Card, Integer> counts = new java.util.HashMap<>();
        for (int index = 0; index < 312; index++) counts.merge(deck.draw(), 1, Integer::sum);
        assertEquals(52, counts.size()); assertTrue(counts.values().stream().allMatch(count -> count == 6));
        assertThrows(IllegalStateException.class, deck::draw);
    }
}
