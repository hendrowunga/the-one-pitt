package routing.rl;

import core.*;
import routing.ActiveRouter;
import routing.MessageRouter;

import java.util.*;

/*
 * © 2025 hendrowunga, University of Sanata Dharma
 * Created on 6/7/25
 */
public class DecentralizedQLearningRouter extends ActiveRouter {
//    // --- Konstanta & Hyperparameters (tetap sama) ---
//    public static final String QL_LEARNING_RATE_S = "learningRate";
//    public static final String QL_DISCOUNT_FACTOR_S = "discountFactor";
//    public static final String QL_EPSILON_S = "epsilon";
//    public static final int ACTION_DO_NOT_FORWARD = 0;
//    public static final int ACTION_FORWARD = 1;
//
//    private double learningRate;
//    private double discountFactor;
//    private double epsilon;
//    private Random rng;
//
//    // Setiap instance dari router ini akan memiliki Q-Table-nya sendiri.
//    private Map<RLState, Map<Integer, Double>> qTable;
//
//    public DecentralizedQLearningRouter(Settings s) {
//        super(s);
//        Settings qlSettings = new Settings("routing");
//        learningRate = qlSettings.getDouble(QL_LEARNING_RATE_S);
//        discountFactor = qlSettings.getDouble(QL_DISCOUNT_FACTOR_S);
//        epsilon = qlSettings.getDouble(QL_EPSILON_S);
//        this.rng = new Random(SimClock.getIntTime());
//
//        // PERUBAHAN UTAMA: Inisialisasi Q-Table untuk setiap instance.
//        this.qTable = new HashMap<>();
//    }
//    @Override
//    public void changedConnection(Connection con) {
//        // Biarkan kosong
//    }
//
//    // Konstruktor copy juga perlu meng-copy Q-Table.
//    // Tapi karena setiap node baru harus mulai dari nol, kita buat Q-Table baru.
//    protected DecentralizedQLearningRouter(DecentralizedQLearningRouter r) {
//        super(r);
//        this.learningRate = r.learningRate;
//        this.discountFactor = r.discountFactor;
//        this.epsilon = r.epsilon;
//        this.rng = r.rng;
//        // Setiap replika/node baru mendapatkan otaknya sendiri yang masih kosong.
//        this.qTable = new HashMap<>();
//    }
//
//    // Metode update() dan semua logika di dalamnya TETAP SAMA PERSIS.
//    // Tidak perlu diubah karena mereka sekarang akan otomatis bekerja pada 'this.qTable'.
//    @Override
//    public void update() {
//        super.update();
//
//        if (isTransferring() || !canStartTransfer()) {
//            return;
//        }
//
//        // Hanya ada satu logika utama: Coba teruskan pesan berdasarkan kebijakan RL
//        tryRLForwarding();
//    }
//
//    /**
//     * Metode ini sekarang menangani SEMUA kasus forwarding.
//     */
//    private Connection tryRLForwarding() {
//        List<Message> messages = new ArrayList<>(this.getMessageCollection());
//        this.sortByQueueMode(messages);
//
//        for (Connection con : getConnections()) {
//            DTNHost peer = con.getOtherNode(getHost());
//            for (Message m : messages) {
//                // Filter dasar: jangan kirim kembali ke hop sebelumnya
//                if (m.getHops().contains(peer)) {
//                    continue;
//                }
//
//                // --- Logika Keputusan RL untuk SEMUA kasus ---
//                // Baik peer adalah tujuan akhir maupun perantara, kita tetap membuat state
//                // dan memutuskan berdasarkan Q-Table.
//                RLState currentState = new RLState(this.getHost(), peer, m);
//                int action = chooseAction(currentState);
//
//                if (action == ACTION_FORWARD) {
//                    int outcome = startTransfer(m, con);
//                    if (outcome == RCV_OK) {
//                        // Jika berhasil memulai satu transfer, hentikan untuk giliran ini
//                        // agar tidak membanjiri koneksi. Ini adalah praktik yang baik.
//                        return con;
//                    }
//                }
//            }
//        }
//        return null;
//    }
//
//    private int chooseAction(RLState state) {
//        // Logika ini sekarang bekerja pada 'this.qTable'
//        this.qTable.putIfAbsent(state, new HashMap<>());
//        this.qTable.get(state).putIfAbsent(ACTION_DO_NOT_FORWARD, 0.0);
//        this.qTable.get(state).putIfAbsent(ACTION_FORWARD, 0.1);
//
//        if (this.rng.nextDouble() < this.epsilon) {
//            return this.rng.nextInt(2);
//        } else {
//            double qValueDoNotForward = this.qTable.get(state).get(ACTION_DO_NOT_FORWARD);
//            double qValueForward = this.qTable.get(state).get(ACTION_FORWARD);
//            return (qValueForward > qValueDoNotForward) ? ACTION_FORWARD : ACTION_DO_NOT_FORWARD;
//        }
//    }
//
//    // Metode ini juga sekarang bekerja pada 'this.qTable'
//    public void updateQValue(RLState state, int action, double reward) {
//        this.qTable.putIfAbsent(state, new HashMap<>());
//        this.qTable.get(state).putIfAbsent(action, 0.0);
//
//        double oldQValue = this.qTable.get(state).get(action);
//        double newQValue = oldQValue + learningRate * (reward - oldQValue);
//        this.qTable.get(state).put(action, newQValue);
//    }
//
//    // TIDAK PERLU LAGI metode getQTable() yang static.
//    // Analisis akan menjadi lebih rumit karena setiap node punya tabel sendiri.
//    // Kita akan membahas cara menganalisisnya nanti.
//
//    @Override
//    public MessageRouter replicate() {
//        return new DecentralizedQLearningRouter(this);
//    }
//    public Map<RLState, Map<Integer, Double>> getQTable() {
//        return this.qTable;
//    }

    // --- Konstanta & Hyperparameters ---
    public static final String QL_LEARNING_RATE_S = "learningRate";
    public static final String QL_DISCOUNT_FACTOR_S = "discountFactor";
    public static final String QL_EPSILON_S = "epsilon";
    public static final String QL_EPSILON_DECAY_S = "epsilonDecay";

    public static final int ACTION_DO_NOT_FORWARD = 0;
    public static final int ACTION_FORWARD = 1;

    // Hyperparameters - sekarang menjadi milik instance, bukan static
    private double learningRate;
    private double discountFactor;
    private double epsilonDecay;
    private double epsilon;
    private Random rng;

    // --- Q-Table & Memori sekarang NON-STATIC ---
    private Map<RLState, Map<Integer, Double>> qTable;
    private Map<String, List<Tuple<RLState, Integer>>> experienceMemory;

    public DecentralizedQLearningRouter(Settings s) {
        super(s);
        Settings qlSettings = new Settings("routing");
        this.learningRate = qlSettings.getDouble(QL_LEARNING_RATE_S);
        this.discountFactor = qlSettings.getDouble(QL_DISCOUNT_FACTOR_S);
        this.epsilon = qlSettings.getDouble(QL_EPSILON_S);
        if (qlSettings.contains(QL_EPSILON_DECAY_S)) {
            this.epsilonDecay = qlSettings.getDouble(QL_EPSILON_DECAY_S);
        } else {
            this.epsilonDecay = 1.0;
        }
        this.rng = new Random(SimClock.getIntTime());
        // Setiap agen mendapatkan otaknya sendiri
        this.qTable = new HashMap<>();
        this.experienceMemory = new HashMap<>();
    }

    protected DecentralizedQLearningRouter(DecentralizedQLearningRouter r) {
        super(r);
        this.learningRate = r.learningRate;
        this.discountFactor = r.discountFactor;
        this.epsilon = r.epsilon;
        this.epsilonDecay = r.epsilonDecay;
        this.rng = r.rng;
        // Setiap replika mendapatkan otaknya sendiri yang kosong
        this.qTable = new HashMap<>();
        this.experienceMemory = new HashMap<>();
    }

    @Override
    public void update() {
        super.update();
        if (this.epsilon > 0.01) {
            this.epsilon *= this.epsilonDecay;
        }
        if (isTransferring() || !canStartTransfer()) {
            return;
        }
        tryRLForwarding();
    }

    // Metode tryRLForwarding, storeExperience, dan chooseAction
    // sama persis dengan versi Baseline, tetapi mereka sekarang
    // bekerja pada qTable dan experienceMemory milik instance (non-static).
    // ... (salin kode tryRLForwarding, storeExperience, chooseAction dari QLearningRouter) ...
    private Connection tryRLForwarding() {
        List<Message> messages = new ArrayList<>(this.getMessageCollection());
        this.sortByQueueMode(messages);
        for (Connection con : getConnections()) {
            DTNHost peer = con.getOtherNode(getHost());
            for (Message m : messages) {
                if (m.getHops().contains(peer)) { continue; }
                RLState currentState = new RLState(this.getHost(), peer, m);
                int action = chooseAction(currentState);
                if (action == ACTION_FORWARD) {
                    storeExperience(m.getId(), currentState, action);
                    int outcome = startTransfer(m, con);
                    if (outcome == RCV_OK) { return con; }
                }
            }
        }
        return null;
    }
    private void storeExperience(String msgId, RLState state, int action) {
        this.experienceMemory.putIfAbsent(msgId, new ArrayList<>());
        this.experienceMemory.get(msgId).add(new Tuple<>(state, action));
    }
    private int chooseAction(RLState state) {
        qTable.putIfAbsent(state, new HashMap<>());
        qTable.get(state).putIfAbsent(ACTION_DO_NOT_FORWARD, 0.0);
        qTable.get(state).putIfAbsent(ACTION_FORWARD, 0.1);

        if (this.rng.nextDouble() < this.epsilon) {
            return this.rng.nextInt(2);
        } else {
            double qValueDoNotForward = qTable.get(state).get(ACTION_DO_NOT_FORWARD);
            double qValueForward = qTable.get(state).get(ACTION_FORWARD);

            // Perbaikan kecil: Jika nilai sama, pilih acak untuk menghindari bias
            if (qValueForward == qValueDoNotForward) {
                return this.rng.nextInt(2);
            }
            return (qValueForward > qValueDoNotForward) ? ACTION_FORWARD : ACTION_DO_NOT_FORWARD;
        }
    }

    // --- Metode Pembelajaran sekarang NON-STATIC ---
    // Dipanggil oleh reporter pada instance spesifik yang terlibat.
    public void learnFromEpisode(String msgId, double finalReward) {
        List<Tuple<RLState, Integer>> episodeHistory = this.experienceMemory.remove(msgId);
        if (episodeHistory == null || episodeHistory.isEmpty()) {
            return;
        }

        double futureRewardEstimate = 0.0;
        for (int i = episodeHistory.size() - 1; i >= 0; i--) {
            Tuple<RLState, Integer> step = episodeHistory.get(i);
            RLState s = step.getKey();
            int a = step.getValue();

            double r;
            if (i == episodeHistory.size() - 1) {
                r = finalReward;
            } else {
                r = this.getIntermediateReward(s, a); // Panggil metode non-static
            }

            // Menggunakan alpha dan gamma milik instance ini
            double alpha = this.learningRate;
            double gamma = this.discountFactor;

            double oldQ = this.qTable.getOrDefault(s, new HashMap<>()).getOrDefault(a, 0.0);
            double newQ = oldQ + alpha * (r + gamma * futureRewardEstimate - oldQ);

            this.qTable.putIfAbsent(s, new HashMap<>());
            this.qTable.get(s).put(a, newQ);

            if (!this.qTable.get(s).isEmpty()) {
                futureRewardEstimate = Collections.max(this.qTable.get(s).values());
            } else {
                futureRewardEstimate = 0.0;
            }
        }
    }

    // Metode untuk reporter agar bisa mengambil Q-Table dari instance ini
    public Map<RLState, Map<Integer, Double>> getQTable() {
        return this.qTable;
    }

    /**
     * Metode helper untuk reward perantara.
     */
    private double getIntermediateReward(RLState state, int action) {
        return -0.01;
    }
    @Override
    public MessageRouter replicate() {
        return new DecentralizedQLearningRouter(this);
    }

}