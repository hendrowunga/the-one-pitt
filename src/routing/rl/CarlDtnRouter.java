package routing.rl;

import core.*;
import routing.ActiveRouter;
import routing.MessageRouter;

import java.util.*;

/**
 *
 * TODO:
 * <h1>CARL-DTN: Context Adaptive Reinforcement Learning based Routing Algorithm in Delay Tolerant Network</h1>
 * <p>
 * Implementasi dari protokol routing CARL-DTN seperti yang dijelaskan dalam paper oleh
 * <strong>Fuad Yimer Yesuf dan M. Prathap</strong> (arXiv:2105.00544v1).
 * </p>
 *
 * <h2>Tujuan & Maksud Protokol</h2>
 * <p>
 * Tujuan utama dari CARL-DTN adalah untuk mengatasi trade-off fundamental dalam Delay Tolerant Networks (DTN)
 * antara <strong>tingkat pengiriman pesan (delivery ratio)</strong> yang tinggi dan <strong>beban jaringan (overhead)</strong> yang rendah.
 * Protokol ini dirancang untuk membuat keputusan perutean yang cerdas dan adaptif dalam lingkungan yang konektivitasnya
 * sering terputus dan tidak dapat diprediksi.
 * </p>
 *
 * <h2>Mekanisme Kunci</h2>
 * <ol>
 *   <li>
 *     <strong>Reinforcement Learning (Q-Learning):</strong> Digunakan untuk pembelajaran jangka panjang. Setiap node
 *     memelihara sebuah Q-Table untuk mengestimasi probabilitas pengiriman pesan ke tujuan akhir melalui
 *     setiap node tetangga yang ditemui. Nilai Q ini diperbarui secara dinamis berdasarkan pertemuan (koneksi naik)
 *     dan penuaan (koneksi turun).
 *   </li>
 *   <li>
 *     <strong>Evaluasi Konteks Adaptif (disederhanakan dari Fuzzy Logic):</strong> Untuk keputusan jangka pendek,
 *     protokol mengevaluasi berbagai konteks secara real-time:
 *     <ul>
 *       <li><strong>Konteks Node:</strong> Kemampuan sebuah node untuk menjadi perantara (berdasarkan sisa buffer, dll.).</li>
 *       <li><strong>Konteks Sosial:</strong> Seberapa "populer" atau terhubungnya sebuah node (berdasarkan popularitas dan keterkinian pertemuan).</li>
 *       <li><strong>Konteks Pesan:</strong> Urgensi sebuah pesan (berdasarkan sisa TTL dan jumlah hop).</li>
 *     </ul>
 *     Dalam implementasi ini, logika Fuzzy yang kompleks disederhanakan menjadi sistem skor berbasis penjumlahan berbobot.
 *   </li>
 *   <li>
 *     <strong>Kontrol Salinan Pesan (Copy Control):</strong> Mengadopsi mekanisme mirip "Spray and Wait" untuk
 *     membatasi jumlah total replika pesan di jaringan, sehingga secara efektif mengendalikan overhead.
 *   </li>
 * </ol>
 *
 * <p>
 * Dengan menggabungkan ketiga mekanisme ini, CARL-DTN bertujuan untuk secara cerdas memilih node perantara (relay) terbaik,
 * memprioritaskan pesan yang paling penting, dan membatasi replikasi yang tidak perlu, yang pada akhirnya menghasilkan
 * kinerja yang unggul dibandingkan dengan protokol DTN tradisional.
 * </p>
 *
 * @author hendrowunga, University of Sanata Dharma
 * @version 1.0
 * @since 6/9/25
 */
public class CarlDtnRouter extends ActiveRouter {
    // -- Q-Learning Parameters -- //

    /** CarlDtnRouter router's settings name space ({@value}) */
    public static final String CARL_ROUTER_NS = "CarlDtnRouter";
    /** Q-Learning: learning rate (alpha) setting ID {@value} */
    public static final String Q_LEARNING_ALPHA_S = "alpha";
    /** Q-Learning: discount factor (gamma) setting ID {@value} */
    public static final String Q_LEARNING_GAMMA_S = "gamma";
    /** Q-Learning: aging factor (beta) setting ID {@value} */
    public static final String Q_LEARNING_BETA_S = "beta";

    // -- Social Metrics Parameters -- //
    /**
     * Popularity: time window for counting encounters (in seconds) setting ID {@value}
     */
    public static final String POPULARITY_WINDOW_S = "popularityWindow";
    /** Popularity: threshold for normalization setting ID {@value} */
    public static final String POPULARITY_THRESHOLD_S = "popularityThreshold";

    // -- Message Copy Control Parameters -- //
    /** Initial number of message copies (L) setting ID {@value} */
    public static final String NROF_COPIES_S = "nrofCopies";

    protected double alpha;
    protected double gamma;
    protected double beta;
    protected double popularityWindow;
    protected double popularityThreshold;
    protected int initialNrofCopies;

    /**
     * Q-Table: Map<Destination, Map<NextHop, Q-Value>>
     * Stores the estimated delivery probability for a destination via a specific
     * next hop.
     */
    private Map<DTNHost, Map<DTNHost, Double>> qTable;

    /** Tracks the number of copies for each message. Map<MessageID, CopyCount> */
    private Map<String, Integer> msgCopies;

    /**
     * Stores recent encounter timestamps for calculating popularity. Map<Node,
     * List<Timestamp>>
     */
    private Map<DTNHost, List<Double>> encounterHistory;

    /**
     * Stores the last encounter time with other nodes for aging Q-values. Map<Node,
     * Timestamp>
     */
    private Map<DTNHost, Double> lastEncounterTime;

    public CarlDtnRouter(Settings s) {
        super(s);
        // Load settings with default values
        Settings car = new Settings(CARL_ROUTER_NS);

        this.alpha = car.contains(Q_LEARNING_ALPHA_S) ? car.getDouble(Q_LEARNING_ALPHA_S) : 0.3;
        this.gamma = car.contains(Q_LEARNING_GAMMA_S) ? car.getDouble(Q_LEARNING_GAMMA_S) : 0.7;
        this.beta = car.contains(Q_LEARNING_BETA_S) ? car.getDouble(Q_LEARNING_BETA_S) : 0.98;
        this.popularityWindow = car.contains(POPULARITY_WINDOW_S) ? car.getInt(POPULARITY_WINDOW_S) : 200;
        this.popularityThreshold = car.contains(POPULARITY_THRESHOLD_S) ? car.getInt(POPULARITY_THRESHOLD_S) : 50;
        this.initialNrofCopies = car.contains(NROF_COPIES_S) ? car.getInt(NROF_COPIES_S) : 8;
    }

    protected CarlDtnRouter(CarlDtnRouter r) {
        super(r);
        this.alpha = r.alpha;
        this.gamma = r.gamma;
        this.beta = r.beta;
        this.popularityWindow = r.popularityWindow;
        this.popularityThreshold = r.popularityThreshold;
        this.initialNrofCopies = r.initialNrofCopies;

        // Initialize data structures for the new router instance
        this.qTable = new HashMap<>();
        this.msgCopies = new HashMap<>();
        this.encounterHistory = new HashMap<>();
        this.lastEncounterTime = new HashMap<>();
    }

    @Override
    public void initialize(DTNHost host, List<MessageListener> mListeners) {
        super.initialize(host, mListeners);
        this.qTable = new HashMap<>();
        this.msgCopies = new HashMap<>();
        this.encounterHistory = new HashMap<>();
        this.lastEncounterTime = new HashMap<>();
    }

    @Override
    public MessageRouter replicate() {
        return new CarlDtnRouter(this);
    }

    /**
     * Called when a new message is created at this host.
     * Initializes the number of copies for the new message.
     */
    @Override
    public boolean createNewMessage(Message m) {
        makeRoomForNewMessage(m.getSize());
        msgCopies.put(m.getId(), initialNrofCopies);
        return super.createNewMessage(m);
    }

    /**
     * Called when a message transfer is complete.
     * The receiving node updates its Q-table based on the sender's knowledge.
     */
    @Override
    protected void transferDone(Connection con) {
        Message m = con.getMessage();
        if (m == null) return;

        // The other node now has the message, so we update our state.
        int copies = msgCopies.getOrDefault(m.getId(), 0);

        if (copies > 1) {
            // "Spray" phase: we gave one copy away
            msgCopies.put(m.getId(), copies / 2);
        } else if (copies == 1) {
            // "Focus" phase: we gave our only copy, so we delete it
            this.deleteMessage(m.getId(), false);
            msgCopies.remove(m.getId());
        }
    }

    /**
     * Called when a connection goes up.
     * Updates social metrics and Q-values according to CARL-DTN logic.
     */
    @Override
    public void changedConnection(Connection con) {
        if (con.isUp()) {
            DTNHost otherHost = con.getOtherNode(getHost());
            updateEncounterMetrics(otherHost);
            updateQValueOnConnectionUp(otherHost);
        } else {
            DTNHost otherHost = con.getOtherNode(getHost());
            updateQValueOnConnectionDown(otherHost);
        }
    }

    /**
     * Main decision-making loop, called at every simulation tick.
     */
    @Override
    public void update() {
        super.update();
        if (isTransferring() || !canStartTransfer()) {
            return; // Busy or nothing to send
        }

        // Try to send messages to directly connected final recipients first
        if (exchangeDeliverableMessages() != null) {
            return;
        }

        // --- CARL-DTN Forwarding Logic --- //
        List<Message> messages = new ArrayList<>(getMessageCollection());
        // Sort messages based on priority (e.g., higher priority first)
        messages.sort(Comparator.comparingDouble(this::calculateMessagePriority).reversed());

        List<Connection> connections = getConnections();
        Collections.shuffle(connections); // Randomize to avoid bias

        for (Connection con : connections) {
            DTNHost otherHost = con.getOtherNode(getHost());
            for (Message m : messages) {
                if (shouldForward(m, otherHost)) {
                    if (startTransfer(m, con) == RCV_OK) {
                        return; // Transfer started, end update loop
                    }
                }
            }
        }
    }

    /**
     * Determines if a message should be forwarded to a candidate host.
     * This implements the logic from Algorithm 4 in the paper.
     *
     * @param m The message to consider forwarding.
     * @param candidate The potential next hop.
     * @return True if the message should be forwarded, false otherwise.
     */
    private boolean shouldForward(Message m, DTNHost candidate) {
        if (hasMessage(m.getId()) && isDeliveredMessage(m)) {
            return false; // Already delivered
        }

        int copies = msgCopies.getOrDefault(m.getId(), 0);
        if (copies == 0) return false; // No copies to give

        if (m.getTo() == candidate) {
            return true; // Final destination
        }

        double myQForCandidate = getQValue(m.getTo(), candidate);
        double mySocialValue = getSocialImportance(getHost());
        double candidateSocialValue = getSocialImportance(candidate);

        // Simple check: Higher Q-value is always better
        if (myQForCandidate > getQValue(m.getTo(), getHost())) { // Simplified self-Q-value
            if (copies > 1) { // Spray phase
                return true;
            } else { // Focus phase (stricter)
                // Forward only if candidate is significantly better
                return myQForCandidate > 0.5; // Example threshold
            }
        }

        // If Q-values are not decisive, use social metrics as a tie-breaker
        if (candidateSocialValue > mySocialValue) {
            if (copies > 1) {
                return true;
            }
        }

        return false;
    }

    // =========================================================================
    // Q-LEARNING IMPLEMENTATION
    // =========================================================================

    private void updateQValueOnConnectionUp(DTNHost otherHost) {
        // This simulates Equation 4 from the paper
        double reward = 1.0; // Simple reward for making a connection
        double transferOpp = calculateTransferOpportunity(otherHost);

        for (DTNHost dest : qTable.keySet()) {
            double oldQ = getQValue(dest, otherHost);
            double maxNextQ = findMaxQForDestFrom(dest, otherHost); // Simplified: Assume we get this info

            double newQ = (1 - alpha) * oldQ + alpha * (reward + gamma * transferOpp * maxNextQ);
            setQValue(dest, otherHost, newQ);
        }
    }

    private void updateQValueOnConnectionDown(DTNHost otherHost) {
        // This simulates Equation 6: Aging/decaying Q-value
        double lastSeen = lastEncounterTime.getOrDefault(otherHost, SimClock.getTime());
        double timeElapsed = SimClock.getTime() - lastSeen;
        double k = timeElapsed / 60.0; // Decay factor per minute

        for (DTNHost dest : qTable.keySet()) {
            double oldQ = getQValue(dest, otherHost);
            double newQ = oldQ * Math.pow(beta, k);
            setQValue(dest, otherHost, newQ);
        }
    }

    private double getQValue(DTNHost dest, DTNHost nextHop) {
        qTable.putIfAbsent(dest, new HashMap<>());
        return qTable.get(dest).getOrDefault(nextHop, 0.0);
    }

    private void setQValue(DTNHost dest, DTNHost nextHop, double value) {
        qTable.putIfAbsent(dest, new HashMap<>());
        qTable.get(dest).put(nextHop, Math.min(1.0, Math.max(0.0, value))); // Clamp between 0 and 1
    }

    private double findMaxQForDestFrom(DTNHost dest, DTNHost from) {
        // In a real scenario, 'from' would need to transmit its Q-table summary.
        // Here, we simplify by just looking at our own Q-table for its neighbors.
        // A more accurate implementation would require a protocol extension.
        if (qTable.containsKey(dest)) {
            return qTable.get(dest).values().stream().max(Double::compare).orElse(0.0);
        }
        return 0.0;
    }

    // =========================================================================
    // FUZZY LOGIC & CONTEXT EVALUATION (Simplified Helper Methods)
    // =========================================================================

    /** Calculates the overall transfer opportunity to a candidate node (simulates FLC4). */
    private double calculateTransferOpportunity(DTNHost candidate) {
        double nodeAbility = getNodeAbility(candidate); // FLC1
        double socialImportance = getSocialImportance(candidate); // FLC2

        // Simplified weighted sum to simulate FLC4
        return (0.6 * nodeAbility) + (0.4 * socialImportance);
    }

    /** Calculates node ability based on buffer and battery (simulates FLC1). */
    private double getNodeAbility(DTNHost node) {
        double freeBufferRatio = (double)node.getRouter().getFreeBufferSize() / node.getRouter().getBufferSize();
        // Assuming battery information is available via an extension
        double batteryLevel = 1.0; // Placeholder

        // Simplified weighted sum
        return (0.7 * freeBufferRatio) + (0.3 * batteryLevel);
    }

    /** Calculates social importance based on popularity and tie-strength (simulates FLC2). */
    private double getSocialImportance(DTNHost node) {
        // Popularity: number of unique nodes met in a time window
        List<Double> history = encounterHistory.getOrDefault(node, new ArrayList<>());
        long recentEncounters = history.stream().filter(t -> SimClock.getTime() - t <= popularityWindow).count();
        double popularity = Math.min(1.0, (double) recentEncounters / popularityThreshold);

        // Tie-Strength: recency of encounter
        double lastSeen = lastEncounterTime.getOrDefault(node, 0.0);
        double recency = Math.exp(-(SimClock.getTime() - lastSeen) / popularityWindow);

        return (0.5 * popularity) + (0.5 * recency);
    }

    /** Calculates message priority based on TTL and hop count (simulates FLC3). */
    private double calculateMessagePriority(Message m) {
        double ttlRatio = 1.0 - ((double) m.getTtl() / m.getCreationTime() + 600); // Normalize TTL
        double hopRatio = Math.min(1.0, (double) m.getHopCount() / 10.0); // Normalize hop count

        // High priority for low TTL (urgent) and high hop count (traveled far)
        return (0.6 * (1 - ttlRatio)) + (0.4 * hopRatio);
    }

    // =========================================================================
    // HELPER & UTILITY METHODS
    // =========================================================================

    private void updateEncounterMetrics(DTNHost otherHost) {
        double now = SimClock.getTime();
        lastEncounterTime.put(otherHost, now);

        // Update encounter history for popularity
        encounterHistory.putIfAbsent(otherHost, new ArrayList<>());
        encounterHistory.get(otherHost).add(now);

        // Prune old history to save memory
        encounterHistory.get(otherHost).removeIf(t -> now - t > popularityWindow);
    }
}