package routing.carl_dtn.util;

import core.SimClock;

/**
 * Kelas data non-statis untuk melacak informasi satu sesi koneksi.
 * Dikelola oleh ContextAwareRLRouter dalam sebuah Map.
 */
public class ConnectionDuration {
    private final double startTime;
    private double endTime;
    private final double totalDurationBeforeThisSession;

    public ConnectionDuration(double previousTotalDuration) {
        this.startTime = SimClock.getTime();
        this.endTime = -1; // -1 means active
        this.totalDurationBeforeThisSession = previousTotalDuration;
    }

    public void endConnection() {
        if (isActive()) {
            this.endTime = SimClock.getTime();
        }
    }

    public boolean isActive() {
        return this.endTime == -1;
    }

    public double getTotalDuration() {
        double currentSessionDuration;
        if (isActive()) {
            currentSessionDuration = SimClock.getTime() - this.startTime;
        } else {
            currentSessionDuration = this.endTime - this.startTime;
        }
        return this.totalDurationBeforeThisSession + currentSessionDuration;
    }

    public double getEndTime() {
        return this.endTime;
    }
}