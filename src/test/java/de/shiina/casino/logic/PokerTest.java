package de.shiina.casino.logic;

import static org.junit.jupiter.api.Assertions.*;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PokerTest {
    private static List<Cards.Card> cards(String text) {
        return java.util.Arrays.stream(text.split(" ")).map(token -> {
            int rank = "23456789TJQKA".indexOf(token.charAt(0)) + 2;
            int suit = "shdc".indexOf(token.charAt(1));
            return new Cards.Card(rank, suit);
        }).toList();
    }
    private static Stream<Arguments> categories() {
        return Stream.of(
                Arguments.of(0, "As Jd 9c 6h 3s 2d 7c"),
                Arguments.of(1, "As Ad 9c 6h 3s 2d 7c"),
                Arguments.of(2, "As Ad 9c 9h 3s 2d 7c"),
                Arguments.of(3, "As Ad Ac 6h 3s 2d 7c"),
                Arguments.of(4, "As 2d 3c 4h 5s 9d Tc"),
                Arguments.of(5, "As Js 9s 6s 3s 2d 7c"),
                Arguments.of(6, "As Ad Ac 6h 6s 2d 7c"),
                Arguments.of(7, "As Ad Ac Ah 3s 2d 7c"),
                Arguments.of(8, "As Ks Qs Js Ts 2d 7c"));
    }
    private void action(Game game, UUID player, String action, long amount, long now) {
        game.guarded(player, game.round(), game.revision(), action, amount, 0, now);
    }
    private UUID current(PokerGame game) {
        return game.view(UUID.randomUUID(), 0).seats().stream().filter(Game.Seat::active).map(seat -> UUID.fromString(seat.id())).findFirst().orElseThrow();
    }
    private Rules cheapRules() {
        Rules rules = Rules.defaults();
        return new Rules(100,2,10000,2,10000,1,2,20,30,8,12,32,rules.wheel(),true,true,true,true);
    }
    private void playChecks(PokerGame game) {
        for (int iteration = 0; iteration < 100 && !game.view(UUID.randomUUID(), 0).phase().equals("result"); iteration++) {
            UUID player = current(game); Game.View view = game.view(player, 0);
            action(game, player, view.actions().contains("check") ? "check" : "call", 0, 21000 + iteration);
        }
        assertEquals("result", game.view(UUID.randomUUID(), 0).phase());
    }

    @ParameterizedTest @MethodSource("categories")
    void allNineCategoriesChooseBestFiveOfSeven(int expected, String hand) {
        assertEquals(expected, PokerHands.category(PokerHands.evaluate(cards(hand))));
    }
    @Test void tieBreakersUseEveryNecessaryKickerAndLowAce() {
        String[][] comparisons = {
                {"As Ks Qd 9h 3c", "As Ks Jd 9h 3c"},
                {"As Ad Kd Qh 3c", "As Ad Kc Jh 3s"},
                {"As Ad Kd Kh 3c", "As Ad Qc Qh Ks"},
                {"As Ad Ac Kh 3c", "As Ad Ac Qh Js"},
                {"6s 5d 4c 3h 2s", "As 5d 4c 3h 2s"},
                {"As Ks Qs 9s 3s", "As Ks Js 9s 3s"},
                {"As Ad Ac Kh Kc", "As Ad Ac Qh Qc"},
                {"As Ad Ac Ah Kc", "As Ad Ac Ah Qc"},
                {"6s 5s 4s 3s 2s", "As 5s 4s 3s 2s"}
        };
        for (String[] comparison : comparisons) assertTrue(PokerHands.evaluate(cards(comparison[0])) > PokerHands.evaluate(cards(comparison[1])), comparison[0]);
        assertEquals(PokerHands.evaluate(cards("As Kd Qh Jc Ts")), PokerHands.evaluate(cards("Ad Ks Qc Jh Td")));
        assertThrows(IllegalArgumentException.class, () -> PokerHands.evaluate(cards("As As Qh Jc Ts")));
    }
    @Test void multipleSidePotsRespectEligibilityAndReturnUncalledExcess() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), third = UUID.randomUUID(), fourth = UUID.randomUUID();
        List<PokerHands.Contender> players = List.of(new PokerHands.Contender(first,0,50,false,9),
                new PokerHands.Contender(second,1,100,false,8), new PokerHands.Contender(third,2,150,false,7), new PokerHands.Contender(fourth,3,150,false,6));
        Map<UUID,Long> awards = PokerHands.distribute(players, 0);
        assertEquals(200, awards.get(first)); assertEquals(150, awards.get(second)); assertEquals(100, awards.get(third)); assertEquals(0, awards.get(fourth));
        Map<UUID,Long> folded = PokerHands.distribute(List.of(new PokerHands.Contender(first,0,50,true,9), players.get(1), players.get(2), players.get(3)), 0);
        assertEquals(0, folded.get(first)); assertEquals(350, folded.get(second)); assertEquals(100, folded.get(third));
        Map<UUID,Long> excess = PokerHands.distribute(List.of(new PokerHands.Contender(first,0,300,false,1),
                new PokerHands.Contender(second,1,100,false,9), new PokerHands.Contender(third,2,100,false,2)), 0);
        assertEquals(200, excess.get(first)); assertEquals(300, excess.get(second));
    }
    @Test void tiedPotsAwardOddChipsLeftOfButton() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), third = UUID.randomUUID();
        List<PokerHands.Contender> players = List.of(new PokerHands.Contender(first,0,1,false,10),
                new PokerHands.Contender(second,1,1,false,10), new PokerHands.Contender(third,2,1,true,0));
        Map<UUID,Long> awards = PokerHands.distribute(players, 0);
        assertEquals(1, awards.get(first)); assertEquals(2, awards.get(second));
        Map<UUID,Long> rotated = PokerHands.distribute(players, 1);
        assertEquals(2, rotated.get(first)); assertEquals(1, rotated.get(second));
    }
    @Test void headsUpButtonIsSmallBlindAndActsFirstPreflopLastPostflop() {
        TestBank bank = new TestBank(); UUID first = bank.player(1000), second = bank.player(1000);
        PokerGame game = new PokerGame(bank, Rules.defaults(), new SecureRandom());
        action(game, first, "join", 200, 0); action(game, second, "join", 200, 0); game.timeout(20000);
        assertEquals(0, game.buttonSeat()); assertEquals(first, current(game));
        assertEquals(1, game.view(first,0).seats().getFirst().bet());
        action(game, first, "call", 0, 21000); assertEquals(second, current(game));
        action(game, second, "check", 0, 21001);
        assertEquals("flop", game.view(first,0).phase()); assertEquals(second, current(game));
        playChecks(game); assertEquals(400, game.totalChips());
        game.timeout(game.deadline()); game.timeout(game.deadline());
        assertEquals(1, game.buttonSeat()); assertEquals(second, current(game));
    }
    @Test void minimumRaiseAndShortAllInDoNotReopenRaiseRight() {
        TestBank bank = new TestBank(); UUID first = bank.player(100), shortStack = bank.player(100), third = bank.player(100);
        PokerGame game = new PokerGame(bank, cheapRules(), new SecureRandom());
        action(game, first, "join", 100,0); action(game, shortStack, "join",7,0); action(game,third,"join",100,0); game.timeout(20000);
        assertEquals(first,current(game));
        assertThrows(IllegalArgumentException.class, () -> action(game, first, "raise",3,21000));
        action(game,first,"raise",6,21000); assertEquals(10,game.minimumRaiseTo());
        action(game,shortStack,"allin",0,21001); action(game,third,"call",0,21002);
        assertEquals(first,current(game)); assertFalse(game.view(first,0).actions().contains("raise"));
        assertEquals(1,game.view(first,0).call());
        assertThrows(IllegalArgumentException.class, () -> action(game,first,"raise",11,21003));
        action(game,first,"call",0,21004); assertEquals("flop",game.view(first,0).phase());
        assertEquals(207,game.totalChips());
    }
    @Test void transitionToHeadsUpDoesNotAssignBigBlindTwice() {
        TestBank bank = new TestBank(); UUID first = bank.player(1000), second = bank.player(1000), third = bank.player(1000);
        PokerGame game = new PokerGame(bank,Rules.defaults(),new SecureRandom());
        action(game,first,"join",200,0); action(game,second,"join",200,0); action(game,third,"join",200,0); game.timeout(20000);
        assertEquals(0,game.buttonSeat()); action(game,first,"fold",0,21000); playChecks(game);
        game.depart(first,22000); game.timeout(game.deadline()); game.timeout(game.deadline());
        assertEquals(2,game.buttonSeat()); assertEquals(third,current(game));
        assertEquals(1,game.view(third,0).seats().stream().filter(seat -> seat.id().equals(third.toString())).findFirst().orElseThrow().bet());
    }
    @Test void cumulativeShortAllInsCanReopenOnlyAfterFullRaiseAmount() {
        TestBank bank = new TestBank(); List<UUID> players = new ArrayList<>();
        PokerGame game = new PokerGame(bank, cheapRules(), new SecureRandom());
        for (int buyIn : new int[]{7,10,100,100}) { UUID player = bank.player(100); players.add(player); action(game,player,"join",buyIn,0); }
        game.timeout(20000); assertEquals(players.get(3),current(game));
        action(game,players.get(3),"raise",6,21000);
        action(game,players.get(0),"allin",0,21001); action(game,players.get(1),"allin",0,21002); action(game,players.get(2),"call",0,21003);
        assertTrue(game.view(players.get(3),0).actions().contains("raise"));
        action(game,players.get(3),"raise",14,21004); assertEquals(217,game.totalChips());
    }
    @Test void shortCallRunsBoardAndReturnsUncalledExcess() {
        TestBank bank = new TestBank(); UUID first = bank.player(100), second = bank.player(100);
        PokerGame game = new PokerGame(bank, Rules.defaults(), new SecureRandom());
        action(game,first,"join",100,0); action(game,second,"join",20,0); game.timeout(20000);
        action(game,first,"allin",0,21000); action(game,second,"call",0,21001);
        Game.View result = game.view(first,0); assertEquals("result",result.phase()); assertEquals(5,result.board().size());
        assertTrue(game.view(UUID.randomUUID(),0).seats().stream().allMatch(seat -> seat.cards().equals(List.of(-1,-1))));
        long stacks = result.seats().stream().mapToLong(Game.Seat::stack).sum(); assertEquals(120,stacks);
        assertTrue(result.seats().stream().filter(seat -> seat.id().equals(first.toString())).findFirst().orElseThrow().stack() >= 80);
    }
    @Test void opponentsAndSpectatorsNeverReceivePrivateOrFoldedCards() {
        TestBank bank = new TestBank(); UUID first = bank.player(1000), second = bank.player(1000), observer = UUID.randomUUID();
        PokerGame game = new PokerGame(bank, Rules.defaults(), new SecureRandom());
        action(game,first,"join",200,0); action(game,second,"join",200,0); game.timeout(20000);
        assertTrue(game.view(first,0).seats().getFirst().cards().stream().allMatch(card -> card >= 0));
        assertEquals(List.of(-1,-1),game.view(second,0).seats().getFirst().cards());
        assertTrue(game.view(observer,0).seats().stream().allMatch(seat -> seat.cards().equals(List.of(-1,-1))));
        action(game,first,"fold",0,21000);
        assertEquals("result",game.view(observer,0).phase());
        assertTrue(game.view(observer,0).seats().stream().allMatch(seat -> seat.cards().equals(List.of(-1,-1))));
        assertEquals(400,game.totalChips());
    }
    @Test void timeoutAndDisconnectCheckOrFoldAndCashOutAfterHand() {
        TestBank bank = new TestBank(); UUID first = bank.player(1000), second = bank.player(1000);
        PokerGame game = new PokerGame(bank,Rules.defaults(),new SecureRandom());
        action(game,first,"join",200,0); action(game,second,"join",200,0); game.timeout(20000);
        game.depart(first,21000); assertEquals("result",game.view(second,0).phase());
        assertFalse(game.contains(first)); assertEquals(199,bank.ledger.balance(first));
        game.close(); assertEquals(201,bank.ledger.balance(second)); assertTrue(bank.ledger.pending().isEmpty());
        game.close(); assertEquals(201,bank.ledger.balance(second));
    }
    @Test void restartRestoresContributionsToTheirOwnersAndIdleCashOutPrunesEscrow() {
        TestBank bank = new TestBank(); UUID first = bank.player(1000), second = bank.player(1000);
        PokerGame game = new PokerGame(bank,Rules.defaults(),new SecureRandom());
        action(game,first,"join",200,0); action(game,second,"join",200,0); game.timeout(20000);
        action(game,first,"raise",20,21000); bank.ledger.recoverAll(); bank.ledger.recoverAll();
        assertEquals(200,bank.ledger.balance(first)); assertEquals(200,bank.ledger.balance(second));
        TestBank idle = new TestBank(); UUID single = idle.player(1000);
        PokerGame lobby = new PokerGame(idle,Rules.defaults(),new SecureRandom()); action(lobby,single,"join",200,0);
        lobby.depart(single,1); lobby.close(); assertEquals(200,idle.ledger.balance(single)); assertTrue(idle.ledger.pending().isEmpty());
    }
    @Test void randomizedCompleteHandsConserveEveryChipAcrossSixSeats() {
        Random decisions = new Random(913874);
        for (int hand = 0; hand < 300; hand++) {
            TestBank bank = new TestBank(); PokerGame game = new PokerGame(bank,cheapRules(),new SecureRandom());
            int count = 2 + decisions.nextInt(5); long total = 0;
            for (int seat = 0; seat < count; seat++) {
                int buyIn = 2 + decisions.nextInt(300); total += buyIn;
                action(game,bank.player(1000),"join",buyIn,0);
            }
            game.timeout(20000);
            int steps = 0;
            while (!game.view(UUID.randomUUID(),0).phase().equals("result") && steps++ < 150) {
                UUID player = current(game); Game.View view = game.view(player,0);
                String chosen = decisions.nextInt(7) == 0 ? "fold" : decisions.nextInt(4) == 0 && view.actions().contains("allin")
                        ? "allin" : view.actions().contains("check") ? "check" : "call";
                action(game,player,chosen,0,21000 + steps);
                assertEquals(total,game.totalChips());
            }
            assertTrue(steps < 150); assertEquals("result",game.view(UUID.randomUUID(),0).phase());
            game.close(); assertEquals(total,bank.ledger.balances().values().stream().mapToLong(Long::longValue).sum());
            assertTrue(bank.ledger.pending().isEmpty());
        }
    }
}
