package de.shiina.casino.logic;

import java.util.ArrayList;
import java.util.List;

/** Small immutable layouts. North is the model's unrotated orientation. */
public final class StationLayout {
    public record Part(int x, int y, int z, int seat) {
        public Part rotated(int turns) {
            int rx = x, rz = z;
            for (int i = 0; i < Math.floorMod(turns, 4); i++) { int next = -rz; rz = rx; rx = next; }
            return new Part(rx, y, rz, seat);
        }
    }
    public static boolean enlarged(String kind) { return kind.equals("blackjack") || kind.equals("poker") || kind.equals("roulette") || kind.equals("wheel"); }
    public static List<Part> parts(String kind) {
        List<Part> parts = new ArrayList<>();
        if (kind.equals("wheel")) {
            for (int y = 0; y < 3; y++) for (int x = -1; x <= 1; x++)
                if (x != 0 || y != 0) parts.add(new Part(x, y, 0, -1));
        } else if (kind.equals("blackjack") || kind.equals("poker") || kind.equals("roulette")) {
            int minZ = kind.equals("roulette") ? -1 : 0;
            for (int z = minZ; z <= 1; z++) for (int x = -1; x <= 1; x++)
                if (x != 0 || z != 0) parts.add(new Part(x, 0, z, -1));
            for (int i = 0; i < 3; i++) parts.add(new Part(i - 1, 0, minZ - 1, i));
            for (int i = 0; i < 3; i++) parts.add(new Part(i - 1, 0, 2, i + 3));
        }
        return List.copyOf(parts);
    }
    private StationLayout() {}
}
