package routing.carl_dtn.learning;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Merepresentasikan struktur data Q-Table untuk satu node.
 * Strukturnya: Map<DestinationID, Map<NextHopID, Q-Value>>
 */
public class QTable {
    private final String ownerId;
    private final Map<String, Map<String, Double>> qTable;

    public QTable(String ownerId) {
        this.ownerId = ownerId;
        this.qTable = new HashMap<>();
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void initializeAllQvalues(Set<String> allNodeIds) {
        for (String destinationId : allNodeIds) {
            if (!destinationId.equals(ownerId)) {
                qTable.put(destinationId, new HashMap<>());
            }
        }
    }

    public double getQvalue(String destinationId, String nextHop) {
        return qTable.getOrDefault(destinationId, new HashMap<>()).getOrDefault(nextHop, 0.0);
    }

    public void updateQvalue(String destinationId, String nextHop, double qValue) {
        qValue = Math.min(qValue, 1.0); // Normalisasi
        qTable.computeIfAbsent(destinationId, k -> new HashMap<>()).put(nextHop, qValue);
    }

    public boolean hasAction(String destinationId, String nextHop) {
        return qTable.containsKey(destinationId) && qTable.get(destinationId).containsKey(nextHop);
    }

    public Set<String> getAllDestinations() {
        return qTable.keySet();
    }

    public Map<String, Double> getActionsForDestination(String destination) {
        return qTable.get(destination);
    }

    public double getMaxQvalueForDestination(String destinationId, Set<String> possibleNextHops) {
        Map<String, Double> actions = getActionsForDestination(destinationId);
        if (actions == null || actions.isEmpty()) return 0.0;

        double maxQ = 0.0;
        for (String nextHop : possibleNextHops) {
            maxQ = Math.max(maxQ, actions.getOrDefault(nextHop, 0.0));
        }
        return maxQ;
    }
}