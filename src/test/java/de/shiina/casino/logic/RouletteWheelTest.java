package de.shiina.casino.logic;

import static org.junit.jupiter.api.Assertions.*;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RouletteWheelTest {
    static final class FixedRandom extends SecureRandom {
        private final int result;
        FixedRandom(int result) { this.result = result; }
        @Override public int nextInt(int bound) { return Math.floorMod(result, bound); }
    }
    private void action(Game game, UUID player, String action, long amount, int option, long now) {
        game.guarded(player, game.round(), game.revision(), action, amount, option, now);
    }

    @Test void europeanWheelHasExactly37UniqueNumbersAndCorrectColors() {
        assertEquals(37, Arrays.stream(Roulette.ORDER).distinct().count());
        assertEquals(666, Arrays.stream(Roulette.ORDER).sum());
        assertArrayEquals(new int[]{0,32,15,19,4,21,2,25,17,34,6,27,13,36,11,30,8,23,10,5,24,16,33,1,20,14,31,9,22,18,29,7,28,12,35,3,26}, Roulette.ORDER);
        assertEquals(18, java.util.stream.IntStream.rangeClosed(1,36).filter(Roulette::red).count());
        assertTrue(Roulette.red(1)); assertTrue(Roulette.red(36)); assertFalse(Roulette.red(28)); assertFalse(Roulette.red(0));
    }
    @ParameterizedTest @EnumSource(Roulette.Kind.class)
    void zeroLosesEveryOutsideBet(Roulette.Kind kind) {
        int number = kind == Roulette.Kind.NUMBER ? 0 : 1;
        assertEquals(kind == Roulette.Kind.NUMBER ? 360 : 0, Roulette.payout(kind, number, 10, 0));
    }
    @Test void everyBetHasCorrectTotalPayout() {
        assertEquals(360, Roulette.payout(Roulette.Kind.NUMBER, 17, 10, 17));
        assertEquals(20, Roulette.payout(Roulette.Kind.RED, 0, 10, 1));
        assertEquals(20, Roulette.payout(Roulette.Kind.BLACK, 0, 10, 2));
        assertEquals(20, Roulette.payout(Roulette.Kind.EVEN, 0, 10, 2));
        assertEquals(20, Roulette.payout(Roulette.Kind.ODD, 0, 10, 3));
        assertEquals(20, Roulette.payout(Roulette.Kind.LOW, 0, 10, 18));
        assertEquals(20, Roulette.payout(Roulette.Kind.HIGH, 0, 10, 19));
        for (int number = 1; number <= 36; number++) {
            assertEquals(30, Roulette.payout(Roulette.Kind.DOZEN, (number - 1) / 12 + 1, 10, number));
            assertEquals(30, Roulette.payout(Roulette.Kind.COLUMN, (number - 1) % 3 + 1, 10, number));
        }
        assertThrows(IllegalArgumentException.class, () -> Roulette.payout(Roulette.Kind.NUMBER, 37, 10, 2));
        assertThrows(IllegalArgumentException.class, () -> Roulette.payout(Roulette.Kind.COLUMN, 0, 10, 2));
    }
    @Test void multiplayerBetsLockAndCancellationRefundsOnce() {
        TestBank bank = new TestBank(); UUID first = bank.player(100), second = bank.player(100);
        RouletteGame game = new RouletteGame(bank, Rules.defaults(), new FixedRandom(1));
        action(game, first, "roulette_red", 10, 0, 0);
        long revision = game.revision(); action(game, second, "roulette_number", 10, 1, 0);
        assertThrows(IllegalArgumentException.class, () -> game.guarded(first, game.round(), revision, "cancel", 0, 0, 1000));
        action(game, first, "cancel", 0, 0, 1000); assertEquals(10, bank.ledger.balance(first));
        assertThrows(IllegalArgumentException.class, () -> action(game, first, "cancel", 0, 0, 1001));
        game.timeout(20000); assertEquals(1, game.view(first, 20000).result());
        assertThrows(IllegalArgumentException.class, () -> action(game, second, "cancel", 0, 0, 20001));
        game.timeout(25000); game.timeout(25001);
        assertEquals(360, bank.ledger.balance(second));
    }
    @Test void restartBeforeResultRefundsAndAfterResultPaysStoredOutcome() {
        TestBank before = new TestBank(); UUID player = before.player(100);
        RouletteGame game = new RouletteGame(before, Rules.defaults(), new FixedRandom(0));
        action(game, player, "roulette_number", 10, 0, 0); before.ledger.recoverAll();
        assertEquals(10, before.ledger.balance(player));
        TestBank after = new TestBank(); UUID winner = after.player(100);
        RouletteGame spin = new RouletteGame(after, Rules.defaults(), new FixedRandom(0));
        action(spin, winner, "roulette_number", 10, 0, 0); spin.timeout(20000);
        assertEquals(360, after.ledger.pending().values().iterator().next().recovery().get(winner));
        spin.close(); spin.close(); assertEquals(360, after.ledger.balance(winner));
    }
    @Test void wagerCountAndOwnershipAreBounded() {
        TestBank bank = new TestBank(); UUID first = bank.player(1000), second = bank.player(1000);
        RouletteGame game = new RouletteGame(bank, Rules.defaults(), new FixedRandom(1));
        for (int index = 0; index < 12; index++) action(game, first, "roulette_red", 10, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> action(game, first, "roulette_red", 10, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> action(game, second, "cancel", 0, 0, 0));
        assertEquals(880, bank.inventory.get(first));
    }
    @Test void everyWheelSegmentMatchesItsMultiplierAndPaysExactlyOnce() {
        long total = 0;
        for (int segment = 0; segment < 12; segment++) {
            TestBank bank = new TestBank(); UUID player = bank.player(100);
            WheelGame game = new WheelGame(bank, Rules.defaults(), new FixedRandom(segment));
            action(game, player, "spin", 12, 0, 0);
            long revision = game.revision();
            assertEquals(segment, game.view(player, 0).result());
            assertThrows(IllegalArgumentException.class, () -> action(game, player, "spin", 12, 0, 1));
            assertThrows(IllegalArgumentException.class, () -> game.guarded(player, game.round(), revision - 1, "spin", 12, 0, 1));
            game.depart(player, 1000); game.timeout(4000); game.timeout(4001); game.close();
            long expected = 12L * Rules.defaults().wheel().get(segment);
            assertEquals(expected, bank.ledger.balance(player)); total += expected;
        }
        assertEquals(132, total);
    }
    @Test void physicalWheelSerializesPlayersAndRestartPreservesFixedAwards() {
        TestBank bank = new TestBank(); UUID first = bank.player(100), second = bank.player(100);
        WheelGame game = new WheelGame(bank, Rules.defaults(), new FixedRandom(11));
        action(game, first, "spin", 10, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> action(game, second, "spin", 20, 0, 1));
        assertEquals(100,bank.inventory.get(second));
        assertEquals(game.view(first,1).result(),game.view(second,1).result());
        game.timeout(4000); action(game, second,"spin",20,0,4001);
        assertEquals(20,game.view(first,4001).pot()); assertEquals(20, game.view(second,4001).pot());
        bank.ledger.recoverAll(); bank.ledger.recoverAll();
        assertEquals(40, bank.ledger.balance(first)); assertEquals(80, bank.ledger.balance(second));
    }
    @Test void invalidWheelConfigurationsAreRejected() {
        Rules base = Rules.defaults();
        for (List<Integer> wheel : List.<List<Integer>>of(List.of(), List.of(1), List.of(0,-1), List.of(0,1001)))
            assertThrows(IllegalArgumentException.class, () -> new Rules(base.exchangeRate(), base.minBet(), base.maxBet(), base.buyInMin(), base.buyInMax(),
                    base.smallBlind(), base.bigBlind(), base.bettingSeconds(), base.turnSeconds(), base.range(), base.spectators(), base.maxTables(), wheel, true,true,true,true));
    }
}
