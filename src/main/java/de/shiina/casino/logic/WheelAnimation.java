package de.shiina.casino.logic;

/** Shared deterministic math; rendering never chooses or settles an outcome. */
public final class WheelAnimation {
    public static double angle(int segment, int count, double elapsedTicks, int durationTicks) {
        double target = Math.PI / 2 - (Math.max(0, segment) + .5) * Math.PI * 2 / count;
        if (segment < 0) return 0;
        double progress = durationTicks <= 0 ? 1 : Math.max(0, Math.min(1, elapsedTicks / durationTicks));
        return target - Math.PI * 12 * Math.pow(1 - progress, 3);
    }
    public static boolean newer(long incomingSerial, long currentSerial) { return incomingSerial > currentSerial; }
    private WheelAnimation() {}
}
