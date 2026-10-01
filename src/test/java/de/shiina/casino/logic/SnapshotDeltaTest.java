package de.shiina.casino.logic;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.shiina.casino.network.SnapshotDelta;
import java.security.SecureRandom;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SnapshotDeltaTest {
    private final Gson json = new Gson();
    private JsonObject object(String value) { return JsonParser.parseString(value).getAsJsonObject(); }

    @Test void arrayInsertRemovalReplacementAndTruncationRoundTrip() {
        JsonObject state = object("{\"board\":[],\"seats\":[1,2,3],\"phase\":\"lobby\"}");
        for (String next : new String[]{"{\"board\":[1,2,3],\"seats\":[1,7,3],\"phase\":\"flop\"}",
                "{\"board\":[1,2,3,4],\"seats\":[7,3],\"phase\":\"turn\"}",
                "{\"board\":[],\"seats\":[],\"phase\":\"result\"}"}) {
            JsonObject expected = object(next), patch = SnapshotDelta.between(state, expected);
            assertEquals(expected, SnapshotDelta.apply(state, patch)); state = expected;
        }
        assertTrue(SnapshotDelta.between(state, state).isEmpty());
    }
    @Test void onlyChangedSeatIsTransferredAndPrivateCardsRemainRecipientSpecific() {
        TestBank bank = new TestBank(); UUID first = bank.player(1000), second = bank.player(1000), observer = UUID.randomUUID();
        PokerGame game = new PokerGame(bank,Rules.defaults(),new SecureRandom());
        game.guarded(first,game.round(),game.revision(),"join",200,0,0);
        game.guarded(second,game.round(),game.revision(),"join",200,0,0); game.timeout(20000);
        JsonObject before = json.toJsonTree(game.view(observer,20000)).getAsJsonObject();
        game.guarded(first,game.round(),game.revision(),"call",0,0,21000);
        JsonObject after = json.toJsonTree(game.view(observer,21000)).getAsJsonObject();
        JsonObject patch = SnapshotDelta.between(before,after);
        assertEquals(after,SnapshotDelta.apply(before,patch));
        for (var seat : patch.getAsJsonObject("seats").getAsJsonObject("$entries").entrySet()) {
            assertEquals(object("{\"cards\":[-1,-1]}").get("cards"),seat.getValue().getAsJsonObject().get("cards"));
        }
        assertFalse(patch.has("board")); assertFalse(patch.has("dealer"));
        assertTrue(patch.toString().length() < after.toString().length());
    }
    @Test void viewingAnIdleOrSpinningTableDoesNotChangeSnapshotOrStartTime() {
        TestBank bank = new TestBank(); UUID player = bank.player(1000);
        RouletteGame game = new RouletteGame(bank,Rules.defaults(),new RouletteWheelTest.FixedRandom(32));
        assertEquals(game.view(player,0),game.view(player,10000));
        game.guarded(player,game.round(),game.revision(),"roulette_red",20,0,1000); game.timeout(21000);
        assertEquals(game.view(player,21000),game.view(player,22000));
        assertEquals(21000,game.view(player,22000).startedMillis());
        assertEquals(26000,game.view(player,22000).deadlineMillis());
    }
}
