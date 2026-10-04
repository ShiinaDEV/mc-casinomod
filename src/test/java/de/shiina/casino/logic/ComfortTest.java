package de.shiina.casino.logic;

import static org.junit.jupiter.api.Assertions.*;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ComfortTest {
    private static void act(Game game, UUID player, String action, long amount, int option, long now) {
        game.guarded(player, game.round(), game.revision(), action, amount, option, now);
    }
    private static RouletteGame roulette(TestBank bank) { return new RouletteGame(bank, Rules.defaults(), new RouletteWheelTest.FixedRandom(1)); }
    private static void firstRound(RouletteGame game, UUID player) {
        act(game, player, "roulette_red", 10, 0, 0);
        act(game, player, "roulette_number", 20, 1, 1);
        game.timeout(20000); game.timeout(25000); game.timeout(31000);
    }
    @Test void repeatAllRouletteBetsUsesOneDebitAndStaleFastClickCannotRepeatIt() {
        TestBank bank = new TestBank(); UUID player = bank.player(1000);
        RouletteGame game = roulette(bank); firstRound(game, player);
        long revision = game.revision(), round = game.round();
        int[] debitCalls = {0};
        Game.Bank counting = new Game.Bank() {
            public boolean debit(UUID id,long amount) { debitCalls[0]++; return bank.debit(id,amount); }
            public void credit(UUID id,long amount) { bank.credit(id,amount); }
            public void hold(UUID tx,String kind,long r,Map<UUID,Long> amounts) { bank.hold(tx,kind,r,amounts); }
            public void settle(UUID tx) { bank.settle(tx); }
            public boolean available(UUID id) { return true; }
            public String name(UUID id) { return "Test"; }
            public List<PersonalRounds.Wager> lastWagers(UUID id) { return bank.lastWagers(id); }
        };
        RouletteGame second = new RouletteGame(counting, Rules.defaults(), new RouletteWheelTest.FixedRandom(1));
        second.guarded(player,second.round(),second.revision(),"repeat",0,0,32000);
        assertEquals(1,debitCalls[0]);
        assertEquals(2,second.view(player,32000).bets().size());
        act(game,player,"repeat",0,0,32000);
        assertThrows(IllegalArgumentException.class,()->game.guarded(player,round,revision,"repeat",0,0,32001));
        assertEquals(910,bank.inventory.get(player));
        assertEquals(List.of(new PersonalRounds.Wager(Roulette.Kind.RED,0,10),new PersonalRounds.Wager(Roulette.Kind.NUMBER,1,20)),bank.lastWagers(player));
    }
    @Test void insufficientChipsOrNewLimitsRejectEntireRouletteTemplate() {
        TestBank bank = new TestBank(); UUID player = bank.player(100);
        RouletteGame game = roulette(bank); firstRound(game,player);
        bank.inventory.put(player,29L);
        long revision=game.revision();
        assertEquals("casino.error.chips",assertThrows(IllegalArgumentException.class,()->act(game,player,"repeat",0,0,32000)).getMessage());
        assertEquals(29,bank.inventory.get(player)); assertTrue(game.view(player,32000).bets().isEmpty()); assertEquals(revision,game.revision());
        Rules r=Rules.defaults();
        game.nextRules(new Rules(r.exchangeRate(),r.minBet(),15,r.buyInMin(),r.buyInMax(),r.smallBlind(),r.bigBlind(),r.bettingSeconds(),r.turnSeconds(),r.range(),r.spectators(),r.maxTables(),r.wheel(),true,true,true,true));
        bank.inventory.put(player,100L);
        assertThrows(IllegalArgumentException.class,()->act(game,player,"repeat",0,0,32000));
        assertEquals(100,bank.inventory.get(player)); assertTrue(game.view(player,32000).bets().isEmpty());
    }
    @Test void repeatRespectsTotalWagerLimitAndCancelledWagersAreNotSaved() {
        TestBank bank=new TestBank();UUID player=bank.player(1000);RouletteGame game=roulette(bank);
        act(game,player,"roulette_red",10,0,0);act(game,player,"roulette_number",20,1,1);
        act(game,player,"cancel",0,0,2);game.timeout(20000);game.timeout(25000);game.timeout(31000);
        assertEquals(List.of(new PersonalRounds.Wager(Roulette.Kind.NUMBER,1,20)),bank.lastWagers(player));
        for(int i=0;i<12;i++) act(game,player,"roulette_black",2,0,32000+i);
        long chips=bank.inventory.get(player);
        assertThrows(IllegalArgumentException.class,()->act(game,player,"repeat",0,0,32020));
        assertEquals(chips,bank.inventory.get(player));assertEquals(12,game.view(player,32020).bets().size());
    }
    @Test void rouletteTotalsAndHistoryComeFromSettlementExactlyOnce() {
        TestBank bank=new TestBank();UUID first=bank.player(100),second=bank.player(100);RouletteGame game=roulette(bank);
        act(game,first,"roulette_red",10,0,0);act(game,first,"roulette_number",20,1,1);act(game,second,"roulette_black",10,0,2);
        game.timeout(20000);assertTrue(bank.personal.history(first).isEmpty());
        game.timeout(25000);game.timeout(25001);game.close();game.close();
        var result=bank.personal.history(first).getFirst();
        assertEquals(30,result.stake());assertEquals(740,result.payout());assertEquals(710,result.net());
        assertEquals(1,bank.completions.get(first).size());assertEquals(1,bank.completions.get(second).size());
        assertEquals(-10,bank.personal.history(second).getFirst().net());assertEquals(740,bank.ledger.balance(first));
    }
    @Test void lateBetIsRejectedEvenWithoutSchedulerAndOldRoundCannotDebit() {
        TestBank bank=new TestBank();UUID player=bank.player(100),other=bank.player(100);RouletteGame game=roulette(bank);
        act(game,player,"roulette_red",10,0,0);long round=game.round(),revision=game.revision();
        assertEquals("casino.error.betting_closed",assertThrows(IllegalArgumentException.class,()->game.guarded(other,round,revision,"roulette_red",10,0,20000)).getMessage());
        assertEquals(100,bank.inventory.get(other));
        game.timeout(25000);game.timeout(31000);
        assertThrows(IllegalArgumentException.class,()->game.guarded(player,round,revision,"repeat",0,0,32000));
        assertEquals(90,bank.inventory.get(player));
    }
    @Test void blackjackDoubleIncludesBothDebitsAndNoHistoryForAbortedHand() {
        TestBank bank=new TestBank();UUID player=bank.player(100);
        List<Cards.Card> cards=new java.util.ArrayList<>(List.of(new Cards.Card(5,0),new Cards.Card(10,0),new Cards.Card(6,0),new Cards.Card(7,0),new Cards.Card(10,1)));
        while(cards.size()<312) cards.add(new Cards.Card(2,0));
        BlackjackGame game=new BlackjackGame(bank,Rules.defaults(),new Cards.Deck(cards));
        act(game,player,"bet",20,0,0);game.timeout(20000);act(game,player,"double",0,0,21000);
        while(!game.view(player,0).phase().equals("result")) game.timeout(game.deadline());
        var result=bank.personal.history(player).getFirst();assertEquals(40,result.stake());assertEquals(80,result.payout());assertEquals(40,result.net());
        assertEquals(20,bank.personal.lastBet(player,"blackjack"));game.close();game.close();assertEquals(1,bank.completions.get(player).size());
        TestBank aborted=new TestBank();UUID p=aborted.player(100);BlackjackGame refund=new BlackjackGame(aborted,Rules.defaults(),new SecureRandom());
        act(refund,p,"bet",20,0,0);refund.close();assertTrue(aborted.personal.history(p).isEmpty());assertEquals(20,aborted.ledger.balance(p));
    }
    @Test void wheelFixedOutcomeOnCloseCompletesOnceAndRemembersStake() {
        TestBank bank=new TestBank();UUID player=bank.player(100);WheelGame game=new WheelGame(bank,Rules.defaults(),new RouletteWheelTest.FixedRandom(11));
        act(game,player,"spin",20,0,0);assertEquals(20,bank.personal.lastBet(player,"wheel"));
        game.close();game.close();game.timeout(99999);
        assertEquals(1,bank.completions.get(player).size());assertEquals(60,bank.personal.history(player).getFirst().net());assertEquals(80,bank.ledger.balance(player));
    }
    @Test void sessionHistoryIsPrivateBoundedDeduplicatedAndClearedOnLogout() {
        PersonalRounds personal=new PersonalRounds();UUID first=UUID.randomUUID(),second=UUID.randomUUID(),tx=UUID.randomUUID();
        personal.open(first);personal.open(second);
        for(int i=0;i<25;i++) personal.complete(first,new PersonalRounds.Result("poker",tx,i,20,40));
        personal.complete(first,new PersonalRounds.Result("poker",tx,24,20,40));
        assertEquals(10,personal.history(first).size());assertEquals(24,personal.history(first).getFirst().round());assertEquals(15,personal.history(first).getLast().round());
        assertTrue(personal.history(second).isEmpty());assertThrows(UnsupportedOperationException.class,()->personal.history(first).clear());
        personal.remember(first,"wheel",20);personal.forget(first);personal.complete(first,new PersonalRounds.Result("wheel",tx,26,20,0));
        assertTrue(personal.history(first).isEmpty());assertEquals(0,personal.lastBet(first,"wheel"));assertEquals(1,personal.size());
        personal.open(first);assertTrue(personal.history(first).isEmpty());personal.clear();assertEquals(0,personal.size());
    }
    @Test void pokerHandTotalsIncludeUncalledReturnAndUseStackAwardsNotBuyIn() {
        TestBank bank=new TestBank();UUID first=bank.player(100),second=bank.player(100);
        PokerGame game=new PokerGame(bank,Rules.defaults(),new RouletteWheelTest.FixedRandom(1));
        act(game,first,"join",100,0,0);act(game,second,"join",20,0,1);game.timeout(20001);
        act(game,first,"allin",0,0,21000);act(game,second,"call",0,0,21001);
        var a=bank.personal.history(first).getFirst();var b=bank.personal.history(second).getFirst();
        assertEquals(100,a.stake());assertEquals(20,b.stake());assertEquals(120,a.payout()+b.payout());assertTrue(a.payout()>=80);
        assertEquals(game.view(first,21001).seats().stream().filter(seat->seat.id().equals(first.toString())).findFirst().orElseThrow().stack(),a.payout());
        assertEquals(0,a.net()+b.net());game.depart(first,21002);game.close();game.close();
        assertEquals(1,bank.completions.get(first).size());assertEquals(1,bank.completions.get(second).size());
        assertEquals(120,bank.ledger.balance(first)+bank.ledger.balance(second));
    }
    @Test void pokerFoldReportsOnlyBlindsAndWinnerNetExcludesReturnedExcess() {
        TestBank bank=new TestBank();UUID first=bank.player(1000),second=bank.player(1000);
        PokerGame game=new PokerGame(bank,Rules.defaults(),new SecureRandom());
        act(game,first,"join",200,0,0);act(game,second,"join",200,0,1);game.timeout(20001);
        assertEquals("casino.error.turn",assertThrows(IllegalArgumentException.class,()->act(game,second,"fold",0,0,21000)).getMessage());
        act(game,first,"fold",0,0,21000);
        var a=bank.personal.history(first).getFirst();var b=bank.personal.history(second).getFirst();
        assertEquals(1,a.stake());assertEquals(0,a.payout());assertEquals(-1,a.net());
        assertEquals(2,b.stake());assertEquals(3,b.payout());assertEquals(1,b.net());
        game.close();assertEquals(199,bank.ledger.balance(first));assertEquals(201,bank.ledger.balance(second));
    }
    @Test void pokerAllInSidePotsReportEveryContributionExactlyOnceWithoutCardData() {
        TestBank bank=new TestBank();List<UUID> players=List.of(bank.player(1000),bank.player(1000),bank.player(1000));
        PokerGame game=new PokerGame(bank,Rules.defaults(),new RouletteWheelTest.FixedRandom(1));
        for(int i=0;i<3;i++) act(game,players.get(i),"join",50L*(i+1),0,i);
        game.timeout(20001);
        for(int i=0;i<3;i++) {
            UUID active=UUID.fromString(game.view(players.getFirst(),0).seats().stream().filter(Game.Seat::active).findFirst().orElseThrow().id());
            act(game,active,"allin",0,0,21000+i);
        }
        assertEquals("result",game.view(players.getFirst(),0).phase());
        long stake=0,payout=0;
        for(int i=0;i<3;i++) {
            var result=bank.personal.history(players.get(i)).getFirst();assertEquals(50L*(i+1),result.stake());
            assertEquals(1,bank.completions.get(players.get(i)).size());stake+=result.stake();payout+=result.payout();
        }
        assertEquals(300,stake);assertEquals(stake,payout);
        assertTrue(game.view(UUID.randomUUID(),0).seats().stream().allMatch(seat->seat.cards().equals(List.of(-1,-1))));
        game.close();assertEquals(300,players.stream().mapToLong(bank.ledger::balance).sum());
    }
}
