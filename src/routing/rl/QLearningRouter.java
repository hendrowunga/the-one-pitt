package routing.rl;

import core.*;
import routing.ActiveRouter;
import routing.MessageRouter;

import java.util.*;

/**
 * Implementasi router Q-Learning dengan arsitektur TERPUSAT (Baseline).
 * Semua agen berbagi satu Q-Table yang sama (static).
 * Logika update telah diperbaiki untuk memastikan pembelajaran holistik.
 *
 * © 2025 hendrowunga, University of Sanata Dharma
 */
//public class QLearningRouter extends ActiveRouter {
//
//    // --- Konstanta & Hyperparameters ---
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
//    // --- Q-Table TERPUSAT: 'static' adalah kuncinya ---
//    private static Map<RLState, Map<Integer, Double>> qTable = new HashMap<>();
//
//
//    public QLearningRouter(Settings s) {
//        super(s);
//        Settings qlSettings = new Settings("routing");
//        learningRate = qlSettings.getDouble(QL_LEARNING_RATE_S);
//        discountFactor = qlSettings.getDouble(QL_DISCOUNT_FACTOR_S);
//        epsilon = qlSettings.getDouble(QL_EPSILON_S);
//        this.rng = new Random(SimClock.getIntTime());
//    }
//
//    protected QLearningRouter(QLearningRouter r) {
//        super(r);
//        this.learningRate = r.learningRate;
//        this.discountFactor = r.discountFactor;
//        this.epsilon = r.epsilon;
//        this.rng = r.rng;
//    }
//
//    @Override
//    public void changedConnection(Connection con) {
//        // Biarkan kosong, logika utama ada di update()
//    }
//
//    // PERBAIKAN: Hapus metode messageTransferred dan messageDeleted dari sini.
//    // Tempatnya ada di RLRewardReporter.
//
//    /**
//     * Metode update() yang DIDESAIN ULANG dan BENAR.
//     * Tidak lagi memisahkan pengiriman langsung. Semua keputusan melalui RL.
//     */
//    @Override
//    public void update() {
//        super.update();
//
//        if (isTransferring() || !canStartTransfer()) {
//            return;
//        }
//
//        // Langsung panggil logika RL forwarding untuk semua kasus.
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
//                // Filter dasar
//                if (m.getHops().contains(peer)) {
//                    continue;
//                }
//
//                // Buat state dan ambil keputusan RL
//                RLState currentState = new RLState(this.getHost(), peer, m);
//                int action = chooseAction(currentState);
//
//                if (action == ACTION_FORWARD) {
//                    int outcome = startTransfer(m, con);
//                    if (outcome == RCV_OK) {
//                        return con; // Berhasil, hentikan untuk giliran ini
//                    }
//                }
//            }
//        }
//        return null;
//    }
//
//    private int chooseAction(RLState state) {
//        qTable.putIfAbsent(state, new HashMap<>());
//        qTable.get(state).putIfAbsent(ACTION_DO_NOT_FORWARD, 0.0);
//        qTable.get(state).putIfAbsent(ACTION_FORWARD, 0.1); // Optimistic initialization
//
//        if (this.rng.nextDouble() < this.epsilon) {
//            return this.rng.nextInt(2);
//        } else {
//            double qValueDoNotForward = qTable.get(state).get(ACTION_DO_NOT_FORWARD);
//            double qValueForward = qTable.get(state).get(ACTION_FORWARD);
//            return (qValueForward > qValueDoNotForward) ? ACTION_FORWARD : ACTION_DO_NOT_FORWARD;
//        }
//    }
//
//    /**
//     * Metode ini dipanggil oleh RLRewardReporter untuk memperbarui Q-Table bersama.
//     */
//    public void updateQValue(RLState state, int action, double reward) {
//        qTable.putIfAbsent(state, new HashMap<>());
//        qTable.get(state).putIfAbsent(action, 0.0);
//
//        double oldQValue = qTable.get(state).get(action);
//        double newQValue = oldQValue + learningRate * (reward - oldQValue);
//        qTable.get(state).put(action, newQValue);
//    }
//
//    /**
//     * Metode static untuk reporter agar bisa mengambil Q-Table bersama.
//     */
//    public static Map<RLState, Map<Integer, Double>> getQTable() {
//        return qTable;
//    }
//
//    @Override
//    public MessageRouter replicate() {
//        return new QLearningRouter(this);
//    }
//}



public class QLearningRouter extends ActiveRouter {

    // --- Konstanta & Hyperparameters ---
    public static final String QL_LEARNING_RATE_S = "learningRate";
    public static final String QL_DISCOUNT_FACTOR_S = "discountFactor";
    public static final String QL_EPSILON_S = "epsilon";
    public static final String QL_EPSILON_DECAY_S = "epsilonDecay"; // BARU: untuk epsilon decay

    public static final int ACTION_DO_NOT_FORWARD = 0;
    public static final int ACTION_FORWARD = 1;

    // Hyperparameters yang dibagikan (static)
    private static double learningRate;
    private static double discountFactor;
    private static double epsilonDecay;

    // Epsilon per-instance agar bisa decay secara independen
    private double epsilon;
    private Random rng;

    // Struktur data terpusat
    private static Map<RLState, Map<Integer, Double>> qTable = new HashMap<>();
    private static Map<String, List<Tuple<RLState, Integer>>> experienceMemory = new HashMap<>();

    // Blok static initializer untuk membaca settings sekali
    static {
        Settings qlSettings = new Settings("routing");
        learningRate = qlSettings.getDouble(QL_LEARNING_RATE_S);
        discountFactor = qlSettings.getDouble(QL_DISCOUNT_FACTOR_S);
        // Baca epsilon decay, default ke 1.0 (tidak ada decay) jika tidak diset
        if (qlSettings.contains(QL_EPSILON_DECAY_S)) {
            epsilonDecay = qlSettings.getDouble(QL_EPSILON_DECAY_S);
        } else {
            epsilonDecay = 1.0;
        }
    }

    public QLearningRouter(Settings s) {
        super(s);
        Settings qlSettings = new Settings("routing");
        this.epsilon = qlSettings.getDouble(QL_EPSILON_S); // Epsilon awal
        this.rng = new Random(SimClock.getIntTime());
    }

    protected QLearningRouter(QLearningRouter r) {
        super(r);
        this.epsilon = r.epsilon;
        this.rng = r.rng;
    }

    @Override
    public void update() {
        super.update();

        // --- IMPLEMENTASI EPSILON DECAY (opsional) ---
        // Panggil ini di setiap update untuk menurunkan epsilon perlahan
        if (this.epsilon > 0.01) { // Batas bawah epsilon
            this.epsilon *= epsilonDecay;
        }

        if (isTransferring() || !canStartTransfer()) {
            return;
        }
        tryRLForwarding();
    }

    // ... (metode tryRLForwarding, storeExperience, tidak berubah) ...
    private Connection tryRLForwarding() {
        List<Message> messages = new ArrayList<>(this.getMessageCollection());
        this.sortByQueueMode(messages);
        for (Connection con : getConnections()) {
            DTNHost peer = con.getOtherNode(getHost());
            for (Message m : messages) {
                if (m.getHops().contains(peer)) {
                    continue;
                }
                RLState currentState = new RLState(this.getHost(), peer, m);
                int action = chooseAction(currentState);
                if (action == ACTION_FORWARD) {
                    storeExperience(m.getId(), currentState, action);
                    int outcome = startTransfer(m, con);
                    if (outcome == RCV_OK) {
                        return con;
                    }
                }
            }
        }
        return null;
    }
    private void storeExperience(String msgId, RLState state, int action) {
        experienceMemory.putIfAbsent(msgId, new ArrayList<>());
        experienceMemory.get(msgId).add(new Tuple<>(state, action));
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

    public static void learnFromEpisode(String msgId, double finalReward) {
        List<Tuple<RLState, Integer>> episodeHistory = experienceMemory.remove(msgId);
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
                r = getIntermediateReward(s, a); // Panggil metode non-static
            }

            double alpha = QLearningRouter.learningRate;
            double gamma = QLearningRouter.discountFactor;

            double oldQ = qTable.getOrDefault(s, new HashMap<>()).getOrDefault(a, 0.0);
            double newQ = oldQ + alpha * (r + gamma * futureRewardEstimate - oldQ);

            qTable.putIfAbsent(s, new HashMap<>());
            qTable.get(s).put(a, newQ);

            if (!qTable.get(s).isEmpty()) {
                futureRewardEstimate = Collections.max(qTable.get(s).values());
            } else {
                futureRewardEstimate = 0.0;
            }
        }
    }

    public static Map<RLState, Map<Integer, Double>> getQTable() {
        return qTable;
    }

    /**
     * Metode helper untuk reward perantara.
     * Dibuat static agar bisa dipanggil dari learnFromEpisode yang static.
     */
    private static double getIntermediateReward(RLState state, int action) {
        // Logika awal: penalti kecil yang konstan untuk setiap hop
        return -0.01;

        // Ide eksperimen masa depan:
        // if (state.getDestinationAffinity() == 0) { // jika meneruskan ke grup yang salah
        //     return -0.05; // penalti lebih besar
        // }
        // return -0.01;
    }

    @Override
    public MessageRouter replicate() {
        return new QLearningRouter(this);
    }
}
