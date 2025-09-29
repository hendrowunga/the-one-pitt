package routing.carl_dtn.util;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Mengimplementasikan Bagian 3.1.1 paper untuk mendeteksi kepadatan node.
 */
public class NetworkDensityCalculator {
    private static final Random random = new Random();

    public static double calculateNodeDensity(int totalNodesInSim, EncounteredNodeSet hostENS, EncounteredNodeSet neighborENS) {
        if (totalNodesInSim <= 0) return 0.0;
        Set<String> uniqueNodes = new HashSet<>();
        if (hostENS != null) uniqueNodes.addAll(hostENS.getAllNodeIds());
        if (neighborENS != null) uniqueNodes.addAll(neighborENS.getAllNodeIds());
        return Math.min((double) uniqueNodes.size() / totalNodesInSim, 1.0);
    }

    public static int calculateCopiesBasedOnDensity(double density) {
        if (density > 0.6) { // Padat
            return 2 + random.nextInt(2); // 2-3 salinan
        } else if (density > 0.3) { // Sedang
            return 4 + random.nextInt(3); // 4-6 salinan
        } else { // Jarang
            return 7 + random.nextInt(4); // 7-10 salinan
        }
    }
}