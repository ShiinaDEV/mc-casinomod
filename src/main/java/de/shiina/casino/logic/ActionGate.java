package de.shiina.casino.logic;

public final class ActionGate {
    private long lastSequence;
    private long lastTick = -1;
    private int packets;

    public boolean accept(long sequence, long tick) {
        if (lastTick != tick) { lastTick = tick; packets = 0; }
        if (++packets > 2 || sequence <= lastSequence || sequence - lastSequence > 64) return false;
        lastSequence = sequence;
        return true;
    }
}
