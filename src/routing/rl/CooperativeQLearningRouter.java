package routing.rl;


import core.*;
import routing.ActiveRouter;
import routing.MessageRouter;

import java.util.*;

/**
 * Implementasi router Q-Learning dengan arsitektur TERDESENTRALISASI KOOPERATIF.
 * Setiap agen belajar secara independen TAPI juga berbagi pengetahuan (Q-Table)
 * saat bertemu dengan agen kooperatif lainnya.
 *
 * © 2025 hendrowunga, University of Sanata Dharma
 */
public class CooperativeQLearningRouter extends ActiveRouter {

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

    private static final double SELF_KNOWLEDGE_WEIGHT = 0.8; // Seberapa besar kita percaya pada diri sendiri
    private static final double PEER_KNOWLEDGE_WEIGHT = 0.2; // Seberapa besar kita percaya pada teman

    // --- Q-Table & Memori sekarang NON-STATIC ---
    private Map<RLState, Map<Integer, Double>> qTable;
    private Map<String, List<Tuple<RLState, Integer>>> experienceMemory;

    public CooperativeQLearningRouter(Settings s) {
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

    protected CooperativeQLearningRouter(CooperativeQLearningRouter r) {
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

        // LANGKAH 1: Lakukan pertukaran pengetahuan (KOOPERASI)
        exchangeKnowledgeWithPeers();

        // LANGKAH 2: Lanjutkan dengan logika forwarding
        if (isTransferring() || !canStartTransfer()) {
            return;
        }
        tryRLForwarding();
    }
    // --- METODE BARU untuk Kooperasi ---
    private void exchangeKnowledgeWithPeers() {
        for (Connection con : getConnections()) {
            DTNHost peer = con.getOtherNode(getHost());
            if (peer.getRouter() instanceof CooperativeQLearningRouter) {
                CooperativeQLearningRouter peerRouter = (CooperativeQLearningRouter) peer.getRouter();
                Map<RLState, Map<Integer, Double>> peerQTable = peerRouter.getQTable();
                mergeQTable(peerQTable);
            }
        }
    }

    private void mergeQTable(Map<RLState, Map<Integer, Double>> peerQTable) {
        for (Map.Entry<RLState, Map<Integer, Double>> peerEntry : peerQTable.entrySet()) {
            RLState state = peerEntry.getKey();
            Map<Integer, Double> peerActions = peerEntry.getValue();
            this.qTable.putIfAbsent(state, new HashMap<>());
            Map<Integer, Double> myActions = this.qTable.get(state);
            for (Map.Entry<Integer, Double> peerActionEntry : peerActions.entrySet()) {
                int action = peerActionEntry.getKey();
                double peerQValue = peerActionEntry.getValue();
                double myQValue = myActions.getOrDefault(action, 0.0);
                double mergedQValue = (SELF_KNOWLEDGE_WEIGHT * myQValue) + (PEER_KNOWLEDGE_WEIGHT * peerQValue);

                myActions.put(action, mergedQValue);
            }
        }
    }

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

    /**
     * Metode helper untuk reward perantara.
     */
    private double getIntermediateReward(RLState state, int action) {
        return -0.01;
    }

    // Metode untuk reporter agar bisa mengambil Q-Table dari instance ini
    public Map<RLState, Map<Integer, Double>> getQTable() {
        return this.qTable;
    }

    @Override
    public MessageRouter replicate() {
        return new CooperativeQLearningRouter(this);
    }
}