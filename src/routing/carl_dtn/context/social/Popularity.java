package routing.carl_dtn.context.social;

import core.DTNHost;

import java.util.HashMap;
import java.util.Map;

/**
 * TODO:
 *
 * @author hendrowunga, University of Sanata Dharma
 * @version 1.0
 * @since 8/24/25
 */
import core.DTNHost;
import core.SimClock;
import routing.carl_dtn.util.EncounteredNodeSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Mengimplementasikan metrik sosial "Popularity" seperti yang dijelaskan di
 * Bagian 3.1.2 paper. Popularitas mengukur seberapa sering sebuah node
 * bertemu dengan node lain dalam jendela waktu tertentu.
 */
public class Popularity {
    private final Map<DTNHost, Double> popularityMap = new HashMap<>();
    private static final int NUM_TH = 15; // Threshold untuk normalisasi
    private static final double TIME_WINDOW = 300.0; // Jendela waktu dalam detik
    private final double alphaPopularity; // Faktor smoothing

    public Popularity(double alphaPopularity) {
        this.alphaPopularity = alphaPopularity;
    }

    /**
     * Memperbarui skor popularitas untuk sebuah node.
     * @param node Node yang popularitasnya akan dihitung.
     * @param ens ENS milik node tersebut.
     */
    public void updatePopularity(DTNHost node, EncounteredNodeSet ens) {
        double currentTime = SimClock.getTime();
        double currentPopularity = popularityMap.getOrDefault(node, 0.0);

        int encounterCount = ens.countRecentEncounters(currentTime, TIME_WINDOW);
        double normalizedPopularity = Math.min((double) encounterCount / NUM_TH, 1.0);

        // Exponential smoothing update
        double updatedPopularity = (1 - alphaPopularity) * currentPopularity + alphaPopularity * normalizedPopularity;
        popularityMap.put(node, updatedPopularity);
    }

    /**
     * Mendapatkan skor popularitas terakhir dari sebuah node.
     * @param node Node yang ingin diketahui popularitasnya.
     * @return Skor popularitas (0.0 - 1.0).
     */
    public double getPopularity(DTNHost node) {
        return popularityMap.getOrDefault(node, 0.0);
    }
}