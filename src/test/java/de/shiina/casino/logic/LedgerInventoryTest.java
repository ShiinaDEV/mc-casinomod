package de.shiina.casino.logic;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LedgerInventoryTest {
    @Test void actionSequencesRejectDuplicatesFloodsAndHugeSequenceJumps() {
        ActionGate gate = new ActionGate();
        assertTrue(gate.accept(1,0)); assertFalse(gate.accept(1,0)); assertFalse(gate.accept(2,0));
        assertTrue(gate.accept(2,1)); assertTrue(gate.accept(3,1)); assertFalse(gate.accept(4,1));
        assertFalse(gate.accept(Long.MAX_VALUE,2)); assertTrue(gate.accept(5,2));
        assertFalse(gate.accept(0,3)); assertFalse(gate.accept(-1,3));
    }
    private record Stack(Object item, int count, boolean custom) {}
    private static final Object CHIP = new Object(), DIAMOND = new Object(), STONE = new Object();
    private static final InventoryOps.Adapter<Stack> ADAPTER = new InventoryOps.Adapter<>() {
        public boolean is(Stack stack, Object item) { return stack.item == item; }
        public boolean mergeable(Stack stack, Object item) { return is(stack, item) && !stack.custom; }
        public int count(Stack stack) { return stack.count; }
        public int maximum(Stack stack) { return 64; }
        public Stack resize(Stack stack, int count) { return new Stack(stack.item, count, stack.custom); }
        public Stack create(Object item, int count) { return new Stack(item, count, false); }
    };

    @Test void exchangeUsesFreedSpaceAndWorksInBothDirections() {
        List<Stack> inventory = List.of(new Stack(DIAMOND, 1, false), new Stack(null, 0, false));
        List<Stack> exchanged = InventoryOps.exchange(inventory, ADAPTER, DIAMOND, 1, CHIP, 100);
        assertNotNull(exchanged); assertEquals(100, exchanged.stream().mapToInt(Stack::count).sum());
        List<Stack> restored = InventoryOps.exchange(exchanged, ADAPTER, CHIP, 100, DIAMOND, 1);
        assertNotNull(restored); assertEquals(1, restored.stream().mapToInt(Stack::count).sum());
        assertSame(DIAMOND, restored.stream().filter(stack -> stack.count > 0).findFirst().orElseThrow().item);
        assertEquals(1, inventory.getFirst().count);
    }
    @Test void fullInventoryRejectsWholeTransactionBeforeRemoval() {
        List<Stack> inventory = List.of(new Stack(DIAMOND, 2, false), new Stack(STONE, 64, false));
        assertNull(InventoryOps.exchange(inventory, ADAPTER, DIAMOND, 1, CHIP, 100));
        assertEquals(2, inventory.getFirst().count);
        assertNull(InventoryOps.exchange(inventory, ADAPTER, CHIP, 100, DIAMOND, 1));
    }
    @Test void identityAndComponentsAreNotNames() {
        List<Stack> inventory = List.of(new Stack(new Object(), 64, false), new Stack(CHIP, 63, true));
        assertNull(InventoryOps.remove(inventory, ADAPTER, CHIP, 64));
        assertEquals(0, InventoryOps.insert(inventory, ADAPTER, CHIP, 1).delivered());
    }
    @Test void partialClaimOnlySubtractsActualDelivery() {
        Ledger ledger = new Ledger(() -> {}); UUID player = UUID.randomUUID(); ledger.credit(player, 100);
        List<Stack> inventory = List.of(new Stack(CHIP, 63, false), new Stack(STONE, 64, false));
        assertEquals(1, ledger.claim(player, 2304, amount -> InventoryOps.insert(inventory, ADAPTER, CHIP, amount).delivered()));
        assertEquals(99, ledger.balance(player));
        assertEquals(0, ledger.claim(player, 0, amount -> amount)); assertEquals(99, ledger.balance(player));
    }
    @Test void settlingAndRecoveringNeverPaysTwiceAndHistoryIsPruned() {
        Ledger ledger = new Ledger(() -> {}); UUID player = UUID.randomUUID(), transaction = UUID.randomUUID();
        ledger.hold(transaction, "overworld:0,64,0", "roulette:result=32", 1, Map.of(player, 360L));
        assertTrue(ledger.settle(transaction)); assertFalse(ledger.settle(transaction)); ledger.recoverAll();
        assertEquals(360, ledger.balance(player)); assertTrue(ledger.pending().isEmpty());
        ledger.claim(player, 1000, amount -> amount); assertTrue(ledger.balances().isEmpty());
    }
    @Test void limitsAndOverflowAreRejectedWithoutLedgerMutation() {
        assertThrows(IllegalArgumentException.class, () -> Money.checked(-1));
        assertThrows(IllegalArgumentException.class, () -> Money.checked(Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> Money.multiply(Money.LIMIT, 2));
        Ledger ledger = new Ledger(() -> {}); UUID player = UUID.randomUUID(); ledger.credit(player, Money.LIMIT);
        assertThrows(IllegalArgumentException.class, () -> ledger.credit(player, 1));
        assertEquals(Money.LIMIT, ledger.balance(player));
        assertThrows(IllegalStateException.class, () -> ledger.claim(player, 1, amount -> 2));
    }
    @Test void staleRoundAndBusyParticipationCannotDebit() {
        TestBank bank = new TestBank(); UUID player = bank.player(100);
        WheelGame game = new WheelGame(bank, Rules.defaults(), new java.security.SecureRandom());
        assertThrows(IllegalArgumentException.class, () -> game.guarded(player, 0, game.revision(), "spin", 20, 0, 0));
        bank.allowed = false;
        assertThrows(IllegalArgumentException.class, () -> game.guarded(player, game.round(), game.revision(), "spin", 20, 0, 0));
        assertEquals(100, bank.inventory.get(player));
    }
}
