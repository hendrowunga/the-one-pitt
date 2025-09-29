package routing.carl_dtn.context.social;

/**
 * TODO:
 *
 * @author hendrowunga, University of Sanata Dharma
 * @version 1.0
 * @since 8/24/25
 */

import core.DTNHost;
import core.SimClock;
import routing.carl_dtn.util.ConnectionDuration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mengimplementasikan metrik sosial "Tie-Strength" (Kekuatan Ikatan) dari Bagian 3.1.2 paper.
 * Tie-Strength mengukur kekuatan hubungan antara DUA node spesifik berdasarkan:
 * 1. Frequency: Seberapa sering mereka bertemu.
 * 2. Closeness: Berapa total durasi koneksi mereka.
 * 3. Recency: Kapan terakhir kali mereka bertemu.
 */
public class TieStrength {
    private final Map<String, Map<String, Double>> tieStrengthMap = new HashMap<>();
    private final Map<String, Map<String, List<Double>>> pairWiseEncounterHistory = new HashMap<>();

    private static final double RECENCY_WEIGHT = 0.3;
    private static final double FREQUENCY_WEIGHT = 0.5;
    private static final double CLOSENESS_WEIGHT = 0.2;
    private static final double FREQUENCY_TIME_WINDOW = 600.0;

    /**
     * Menghitung dan memperbarui skor Tie-Strength antara dua node.
     * @param host Node pertama.
     * @param neighbor Node kedua.
     * @param connectionHistory History koneksi dari host.
     */
    public void calculateTieStrength(DTNHost host, DTNHost neighbor, Map<DTNHost, ConnectionDuration> connectionHistory) {
        // 1. FREQUENCY
        recordEncounter(host, neighbor);
        int frequency = getFrequency(host, neighbor);
        double normFreq = normalize(frequency, 15.0);

        // 2. CLOSENESS & RECENCY
        ConnectionDuration connection = connectionHistory.get(neighbor);
        double closeness = 0;
        double recencyDecay = 0;

        if (connection != null) {
            closeness = connection.getTotalDuration();
            if (!connection.isActive()) {
                double timeSinceLastEncounter = SimClock.getTime() - connection.getEndTime();
                recencyDecay = Math.exp(-timeSinceLastEncounter / 1000.0);
            } else {
                recencyDecay = 1.0; // Masih terkoneksi, recency maksimal
            }
        }
        double normCloseness = normalize(closeness, 900.0);

        // 3. Gabungkan semua metrik
        double rawScore = (FREQUENCY_WEIGHT * normFreq) + (CLOSENESS_WEIGHT * normCloseness);
        double tieStrength = rawScore * (1 + RECENCY_WEIGHT * recencyDecay);
        tieStrength = Math.min(Math.max(tieStrength, 0.0), 1.0); // Batasi 0-1

        // Simpan secara simetris
        tieStrengthMap.computeIfAbsent(host.toString(), k -> new HashMap<>()).put(neighbor.toString(), tieStrength);
        tieStrengthMap.computeIfAbsent(neighbor.toString(), k -> new HashMap<>()).put(host.toString(), tieStrength);
    }

    public double getTieStrength(DTNHost host, DTNHost neighbor) {
        return tieStrengthMap.getOrDefault(host.toString(), new HashMap<>()).getOrDefault(neighbor.toString(), 0.0);
    }

    private void recordEncounter(DTNHost nodeA, DTNHost nodeB) {
        String id1 = nodeA.toString();
        String id2 = nodeB.toString();
        // Urutkan ID untuk konsistensi
        String key1 = id1.compareTo(id2) < 0 ? id1 : id2;
        String key2 = id1.compareTo(id2) < 0 ? id2 : id1;
        pairWiseEncounterHistory.computeIfAbsent(key1, k -> new HashMap<>())
                .computeIfAbsent(key2, k -> new ArrayList<>())
                .add(SimClock.getTime());
    }

    private int getFrequency(DTNHost nodeA, DTNHost nodeB) {
        String id1 = nodeA.toString();
        String id2 = nodeB.toString();
        String key1 = id1.compareTo(id2) < 0 ? id1 : id2;
        String key2 = id1.compareTo(id2) < 0 ? id2 : id1;

        List<Double> timestamps = pairWiseEncounterHistory
                .getOrDefault(key1, new HashMap<>())
                .getOrDefault(key2, new ArrayList<>());

        double currentTime = SimClock.getTime();
        long count = timestamps.stream().filter(t -> (currentTime - t) <= FREQUENCY_TIME_WINDOW).count();
        return (int) count;
    }

    private double normalize(double value, double max) {
        return Math.min(value / max, 1.0);
    }
}