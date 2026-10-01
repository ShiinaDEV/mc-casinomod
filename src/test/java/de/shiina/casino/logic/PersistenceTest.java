package de.shiina.casino.logic;

import static org.junit.jupiter.api.Assertions.*;
import de.shiina.casino.storage.CasinoSavedData;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class PersistenceTest {
    @Test void nbtRoundTripPreservesBalancesTransactionsLocationsAndFixedPayouts() {
        CasinoSavedData original = new CasinoSavedData();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), roulette = UUID.randomUUID(), poker = UUID.randomUUID();
        original.ledger.credit(first, 12);
        original.ledger.hold(roulette,"table@minecraft:overworld:12,64,-8","roulette:result=0",8,Map.of(first,360L));
        original.ledger.hold(poker,"table@minecraft:the_nether:0,64,0","poker",9,Map.of(first,200L,second,400L));
        CasinoSavedData restored = CasinoSavedData.load(original.save(new CompoundTag(),null),null);
        assertEquals(original.ledger.balances(),restored.ledger.balances());
        assertEquals(original.ledger.pending(),restored.ledger.pending());
        restored.ledger.recoverAll(); restored.ledger.recoverAll();
        assertEquals(572,restored.ledger.balance(first)); assertEquals(400,restored.ledger.balance(second));
        assertTrue(restored.ledger.pending().isEmpty());
        CasinoSavedData secondRestart = CasinoSavedData.load(restored.save(new CompoundTag(),null),null);
        secondRestart.ledger.recoverAll(); assertEquals(572,secondRestart.ledger.balance(first));
    }
}
