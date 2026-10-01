package de.shiina.casino.logic;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ResourceTest {
    private static final Path ROOT = Path.of("src/main/resources");
    private JsonObject read(Path path) throws Exception { return JsonParser.parseString(Files.readString(path)).getAsJsonObject(); }

    @Test void allModelsResolveTexturesAndRecipesAndLootMatchBlocks() throws Exception {
        for (String block : new String[]{"blackjack_table","poker_table","roulette_table","fortune_wheel","chip_exchange"}) {
            JsonObject model = read(ROOT.resolve("assets/shiinacasino/models/block/" + block + ".json"));
            for (var texture : model.getAsJsonObject("textures").entrySet()) {
                String location = texture.getValue().getAsString().replace("shiinacasino:","");
                assertTrue(Files.exists(ROOT.resolve("assets/shiinacasino/textures/" + location + ".png")),location);
            }
            assertEquals(block.equals("poker_table") || block.equals("chip_exchange") ? 4 : 8,read(ROOT.resolve("assets/shiinacasino/blockstates/" + block + ".json")).getAsJsonObject("variants").size());
            assertEquals("shiinacasino:" + block,read(ROOT.resolve("data/shiinacasino/recipe/" + block + ".json")).getAsJsonObject("result").get("id").getAsString());
            assertEquals("shiinacasino:" + block,read(ROOT.resolve("data/shiinacasino/loot_table/blocks/" + block + ".json"))
                    .getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString());
        }
        assertFalse(Files.exists(ROOT.resolve("data/shiinacasino/recipe/casino_chip.json")));
    }
    @Test void pixelTexturesAreValidAndLanguagesHaveExactlyMatchingKeys() throws Exception {
        try (var textures = Files.walk(ROOT.resolve("assets/shiinacasino/textures"))) {
            var files = textures.filter(path -> path.toString().endsWith(".png")).toList();
            assertEquals(14,files.size());
            for (Path path : files) { var image = ImageIO.read(path.toFile()); assertNotNull(image); assertTrue(image.getWidth() > 0 && image.getHeight() > 0); }
        }
        JsonObject german = read(ROOT.resolve("assets/shiinacasino/lang/de_de.json"));
        JsonObject english = read(ROOT.resolve("assets/shiinacasino/lang/en_us.json"));
        assertEquals(german.keySet(),english.keySet()); assertTrue(german.size() > 70);
    }
    @Test void everyStationVariantResolvesAndOwnedPartsDropNoItems() throws Exception {
        Path assets=ROOT.resolve("assets/shiinacasino");
        try(var files=Files.list(assets.resolve("blockstates"))) {
            for(Path path:files.toList()) for(var entry:read(path).getAsJsonObject("variants").entrySet()) {
                String model=entry.getValue().getAsJsonObject().get("model").getAsString().replace("shiinacasino:","");
                assertTrue(Files.exists(assets.resolve("models/"+model+".json")),entry.getKey()+" -> "+model);
            }
        }
        for(String part:new String[]{"blackjack_table_part","roulette_table_part","fortune_wheel_part"}) {
            assertEquals(56,read(assets.resolve("blockstates/"+part+".json")).getAsJsonObject("variants").size());
            assertTrue(read(ROOT.resolve("data/shiinacasino/loot_table/blocks/"+part+".json")).getAsJsonArray("pools").isEmpty());
            assertFalse(Files.exists(ROOT.resolve("data/shiinacasino/recipe/"+part+".json")));
        }
    }
    @Test void fittingsAreFlushUnrotatedAndShareNoCoincidentFacesWithWood() throws Exception {
        int count=0;
        try(var files=Files.list(ROOT.resolve("assets/shiinacasino/models/block"))) {
            for(Path path:files.toList()) {
                var model=read(path);if(!model.has("elements")) continue;
                var elements=model.getAsJsonArray("elements");
                for(var value:elements) {
                    var element=value.getAsJsonObject();var faces=element.getAsJsonObject("faces");
                    if(faces.size()==0 || !faces.entrySet().stream().allMatch(e->e.getValue().getAsJsonObject().get("texture").getAsString().equals("#trim"))) continue;
                    count++;assertFalse(element.has("rotation"),path.toString());
                    var lo=element.getAsJsonArray("from");var hi=element.getAsJsonArray("to");
                    assertEquals(.75,hi.get(0).getAsDouble()-lo.get(0).getAsDouble(),1e-9);
                    assertEquals(.25,hi.get(1).getAsDouble()-lo.get(1).getAsDouble(),1e-9);
                    assertEquals(.75,hi.get(2).getAsDouble()-lo.get(2).getAsDouble(),1e-9);
                    assertEquals(path.getFileName().toString().equals("chip_exchange.json")?16:15,hi.get(1).getAsDouble());
                    assertEquals(3,faces.size());assertTrue(faces.has("up"));assertFalse(faces.has("down"));
                    for(var f:faces.entrySet()) {
                        var face=f.getValue().getAsJsonObject();assertFalse(face.has("rotation"));
                        var uv=face.getAsJsonArray("uv");assertEquals(.75,uv.get(2).getAsDouble());assertEquals(f.getKey().equals("up")?.75:.25,uv.get(3).getAsDouble());
                        for(var other:elements) if(other!=value) for(var otherFace:other.getAsJsonObject().getAsJsonObject("faces").entrySet())
                            assertFalse(overlap(element,f.getKey(),other.getAsJsonObject(),otherFace.getKey()),path+" coincident trim surface");
                    }
                }
            }
        }
        assertEquals(24,count);
    }
    private boolean overlap(JsonObject a,String side,JsonObject b,String otherSide) {
        int axis=axis(side);if(axis!=axis(otherSide)) return false;
        if(a.getAsJsonArray(low(side)?"from":"to").get(axis).getAsDouble()!=b.getAsJsonArray(low(otherSide)?"from":"to").get(axis).getAsDouble()) return false;
        for(int i=0;i<3;i++) if(i!=axis && Math.min(a.getAsJsonArray("to").get(i).getAsDouble(),b.getAsJsonArray("to").get(i).getAsDouble())
                <=Math.max(a.getAsJsonArray("from").get(i).getAsDouble(),b.getAsJsonArray("from").get(i).getAsDouble())) return false;
        return true;
    }
    private int axis(String side) { return side.equals("east") || side.equals("west")?0:side.equals("up") || side.equals("down")?1:2; }
    private boolean low(String side) { return side.equals("west") || side.equals("down") || side.equals("north"); }
}
