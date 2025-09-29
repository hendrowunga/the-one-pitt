package routing.carl_dtn.learning;

import core.DTNHost;
import core.SimClock;
import routing.carl_dtn.ContextAwareRLRouter;
import routing.carl_dtn.util.EncounteredNodeSet;
import java.util.Map;
import java.util.Set;

/**
 * Mengelola semua strategi pembaruan Q-Table sesuai paper:
 * 1. Update saat koneksi UP.
 * 2. Aging/Decay saat koneksi lama terputus.
 * 3. Sinkronisasi saat pesan ditransfer.
 */
public class QLearningStrategy {
    private final QTable qTable;
    private static final double GAMMA = 0.4;
    private static final double ALPHA = 0.6;
    private static final double AGING_CONSTANT = 0.998;
    private static final double MIN_ELAPSED_FOR_AGING = 240.0;
    private static final double MIN_Q_VALUE = 0.05;

    public QLearningStrategy(QTable qTable) {
        this.qTable = qTable;
    }

    public void updateFirstStrategy(DTNHost host, DTNHost neighbor, String destinationId, String nextHop, double fuzzOpp) {
        EncounteredNodeSet neighborENS = ((ContextAwareRLRouter) neighbor.getRouter()).getEncounteredNodeSet();
        double currentQ = qTable.getQvalue(destinationId, nextHop);
        double reward = neighborENS.getAllNodeIds().contains(destinationId) ? 1.0 : 0.0;

        QTable neighborQTable = ((ContextAwareRLRouter) neighbor.getRouter()).getQTable();
        Set<String> neighborPossibleHops = neighborENS.getAllNodeIds();
        double maxQFromNeighborView = neighborQTable.getMaxQvalueForDestination(destinationId, neighborPossibleHops);

        double newQ = ALPHA * (reward + GAMMA * fuzzOpp * maxQFromNeighborView) + (1 - ALPHA) * currentQ;
        qTable.updateQvalue(destinationId, nextHop, newQ);
    }

    public void processDelayedAging(DTNHost host, Map<String, Double> pendingAging) {
        double now = SimClock.getTime();
        pendingAging.entrySet().removeIf(entry -> {
            String neighborId = entry.getKey();
            double lastSeen = entry.getValue();
            if ((now - lastSeen) >= MIN_ELAPSED_FOR_AGING) {
                applyAgingToNeighbor(neighborId, now - lastSeen);
                return true;
            }
            return false;
        });
    }

    private void applyAgingToNeighbor(String neighborId, double elapsedTime) {
        for (String destinationId : qTable.getAllDestinations()) {
            if (qTable.hasAction(destinationId, neighborId)) {
                double currentQ = qTable.getQvalue(destinationId, neighborId);
                double agedQ = Math.max(currentQ * Math.pow(AGING_CONSTANT, elapsedTime), MIN_Q_VALUE);
                qTable.updateQvalue(destinationId, neighborId, agedQ);
            }
        }
    }

    public static void synchronizeQTables(QTable table1, QTable table2) {
        syncOneWay(table1, table2);
        syncOneWay(table2, table1);
    }

    private static void syncOneWay(QTable source, QTable target) {
        for (String dest : source.getAllDestinations()) {
            Map<String, Double> sourceActions = source.getActionsForDestination(dest);
            if (sourceActions == null) continue;

            for (Map.Entry<String, Double> entry : sourceActions.entrySet()) {
                String nextHop = entry.getKey();
                double sourceQ = entry.getValue();
                double targetQ = target.getQvalue(dest, nextHop);
                if (sourceQ > targetQ) {
                    target.updateQvalue(dest, nextHop, sourceQ);
                }
            }
        }
    }
}