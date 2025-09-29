package routing.carl_dtn.util;

import core.DTNHost;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Mengelola "memori" sebuah node tentang node lain yang pernah ditemui.
 */
public class EncounteredNodeSet {
    private final Map<String, EncounteredNode> ensTable;

    public EncounteredNodeSet() {
        this.ensTable = new HashMap<>();
    }

    public void updateENS(DTNHost host, DTNHost neighbor, String neighborId, long time, int energy, int buffer, double popularity) {
        if (neighborId.equals(String.valueOf(host.getAddress()))) return;

        if (ensTable.containsKey(neighborId)) {
            ensTable.get(neighborId).update(time, energy, buffer, popularity);
        } else {
            ensTable.put(neighborId, new EncounteredNode(neighborId, time, energy, buffer, popularity));
        }
    }

    public void mergeENS(EncounteredNodeSet otherENS, String selfId) {
        if (otherENS == null) return;
        for (Map.Entry<String, EncounteredNode> entry : otherENS.ensTable.entrySet()) {
            String nodeId = entry.getKey();
            if (nodeId.equals(selfId)) continue;
            EncounteredNode otherNodeInfo = entry.getValue();
            if (!this.ensTable.containsKey(nodeId) || otherNodeInfo.isMoreRelevantThan(this.ensTable.get(nodeId))) {
                this.ensTable.put(nodeId, otherNodeInfo.clone());
            }
        }
    }

    public void exchangeWith(EncounteredNodeSet otherENS, DTNHost self, DTNHost peer, long currentTime) {
        this.mergeENS(otherENS, String.valueOf(self.getAddress()));
    }

    public void removeOldEncounters() {
        ensTable.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    public void removeEncounter(String nodeId) {
        ensTable.remove(nodeId);
    }

    public Set<String> getAllNodeIds() {
        return new HashSet<>(ensTable.keySet());
    }

    public int countRecentEncounters(double currentTime, double timeWindow) {
        return (int) ensTable.values().stream()
                .filter(node -> (currentTime - node.getEncounterTime()) <= timeWindow)
                .count();
    }

    public EncounteredNodeSet clone() {
        EncounteredNodeSet newSet = new EncounteredNodeSet();
        for (Map.Entry<String, EncounteredNode> entry : this.ensTable.entrySet()) {
            newSet.ensTable.put(entry.getKey(), entry.getValue().clone());
        }
        return newSet;
    }
}