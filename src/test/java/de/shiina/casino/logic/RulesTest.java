package de.shiina.casino.logic;

import static org.junit.jupiter.api.Assertions.*;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RulesTest {
    private Rules disabled() {
        Rules base = Rules.defaults();
        return new Rules(100,2,10000,20,10000,1,2,20,30,8,12,32,List.of(0,9),false,false,false,false);
    }
    @Test void disabledGamesCannotCreateNewDebits() {
        TestBank bank = new TestBank(); UUID player = bank.player(1000);
        for (Game game : List.of(new BlackjackGame(bank,disabled(),new SecureRandom()),new PokerGame(bank,disabled(),new SecureRandom()),
                new RouletteGame(bank,disabled(),new SecureRandom()),new WheelGame(bank,disabled(),new SecureRandom()))) {
            String action = switch (game.kind()) { case "blackjack" -> "bet"; case "poker" -> "join"; case "roulette" -> "roulette_red"; default -> "spin"; };
            assertThrows(IllegalArgumentException.class, () -> game.guarded(player,game.round(),game.revision(),action,200,0,0));
        }
        assertEquals(1000,bank.inventory.get(player));
    }
    @Test void runningRouletteAndWheelKeepTheirAcceptedRulesAndOutcomes() {
        TestBank bank = new TestBank(); UUID roulettePlayer = bank.player(1000), wheelPlayer = bank.player(1000);
        RouletteGame roulette = new RouletteGame(bank,Rules.defaults(),new RouletteWheelTest.FixedRandom(1));
        roulette.guarded(roulettePlayer,roulette.round(),roulette.revision(),"roulette_red",20,0,0);
        roulette.nextRules(disabled()); roulette.timeout(20000); roulette.timeout(25000);
        assertEquals(40,bank.ledger.balance(roulettePlayer));
        WheelGame wheel = new WheelGame(bank,Rules.defaults(),new RouletteWheelTest.FixedRandom(11));
        wheel.guarded(wheelPlayer,wheel.round(),wheel.revision(),"spin",20,0,0); wheel.nextRules(disabled());
        assertEquals(Rules.defaults().wheel(),wheel.view(wheelPlayer,1000).segments());
        wheel.timeout(4000); assertEquals(80,bank.ledger.balance(wheelPlayer));
    }
}
