package de.shiina.casino.logic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Authoritative, server-thread-only occupancy; never persisted across logins. */
public final class SeatRegistry {
    public record Key(String dimension, long station, int index) {
        public Key { if (index < 0 || index >= 6) throw new IllegalArgumentException("seat index"); }
    }
    private final Map<Key, UUID> occupants = new HashMap<>();
    private final Map<UUID, Key> players = new HashMap<>();
    public boolean claim(UUID player, Key seat) {
        if (seat.equals(players.get(player))) return player.equals(occupants.get(seat));
        if (players.containsKey(player) || occupants.containsKey(seat)) return false;
        players.put(player, seat); occupants.put(seat, player); return true;
    }
    public Key seat(UUID player) { return players.get(player); }
    public UUID occupant(Key key) { return occupants.get(key); }
    public Key release(UUID player) {
        Key key = players.remove(player);
        if (key != null) occupants.remove(key, player);
        return key;
    }
    public List<UUID> at(String dimension, long station) {
        return players.entrySet().stream().filter(e -> e.getValue().dimension.equals(dimension) && e.getValue().station == station)
                .map(Map.Entry::getKey).toList();
    }
    public int size() { return players.size(); }
    public void clear() { players.clear(); occupants.clear(); }
}
