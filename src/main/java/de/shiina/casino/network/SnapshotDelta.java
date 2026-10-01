package de.shiina.casino.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;

public final class SnapshotDelta {
    private SnapshotDelta() {}

    public static JsonObject between(JsonObject previous, JsonObject next) {
        JsonObject patch = new JsonObject();
        for (Map.Entry<String, JsonElement> field : next.entrySet()) {
            JsonElement oldValue = previous.get(field.getKey()), newValue = field.getValue();
            if (newValue.equals(oldValue)) continue;
            if (newValue.isJsonArray() && oldValue != null && oldValue.isJsonArray()) {
                JsonArray before = oldValue.getAsJsonArray(), after = newValue.getAsJsonArray();
                JsonObject update = new JsonObject(), entries = new JsonObject();
                update.addProperty("$size", after.size());
                for (int index = 0; index < after.size(); index++)
                    if (index >= before.size() || !after.get(index).equals(before.get(index))) entries.add(Integer.toString(index), after.get(index).deepCopy());
                update.add("$entries", entries); patch.add(field.getKey(), update);
            } else patch.add(field.getKey(), newValue.deepCopy());
        }
        return patch;
    }

    public static JsonObject apply(JsonObject previous, JsonObject patch) {
        JsonObject next = previous.deepCopy();
        for (Map.Entry<String, JsonElement> field : patch.entrySet()) {
            JsonElement value = field.getValue();
            if (value.isJsonObject() && value.getAsJsonObject().has("$size")) {
                JsonObject update = value.getAsJsonObject();
                JsonArray before = previous.getAsJsonArray(field.getKey()), after = new JsonArray();
                int size = update.get("$size").getAsInt();
                if (size < 0 || size > 256) throw new IllegalArgumentException("array size");
                JsonObject entries = update.getAsJsonObject("$entries");
                for (int index = 0; index < size; index++) {
                    JsonElement replacement = entries.get(Integer.toString(index));
                    after.add(replacement == null ? before.get(index).deepCopy() : replacement.deepCopy());
                }
                next.add(field.getKey(), after);
            } else next.add(field.getKey(), value.deepCopy());
        }
        return next;
    }
}
