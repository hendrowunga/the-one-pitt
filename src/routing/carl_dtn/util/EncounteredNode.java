package routing.carl_dtn.util;

import core.SimClock;

/**
 * Representasi satu entri dalam Encountered Node Set (ENS).
 */
public class EncounteredNode {
    private final String nodeId;
    private long encounterTime;
    private double remainingEnergy;
    private int bufferSize;
    private double popularity;

    private static final double ENTRY_TTL = 3600.0;

    public EncounteredNode(String nodeId, long encounterTime, double remainingEnergy, int bufferSize, double popularity) {
        this.nodeId = nodeId;
        this.encounterTime = encounterTime;
        this.remainingEnergy = remainingEnergy;
        this.bufferSize = bufferSize;
        this.popularity = popularity;
    }

    public void update(long encounterTime, double remainingEnergy, int bufferSize, double popularity) {
        this.encounterTime = encounterTime;
        this.remainingEnergy = remainingEnergy;
        this.bufferSize = bufferSize;
        this.popularity = popularity;
    }

    public boolean isMoreRelevantThan(EncounteredNode other) {
        return this.encounterTime > other.encounterTime;
    }

    public boolean isExpired() {
        return (SimClock.getTime() - this.encounterTime) > ENTRY_TTL;
    }

    public EncounteredNode clone() {
        return new EncounteredNode(this.nodeId, this.encounterTime, this.remainingEnergy, this.bufferSize, this.popularity);
    }

    public String getNodeId() { return nodeId; }
    public long getEncounterTime() { return encounterTime; }
}