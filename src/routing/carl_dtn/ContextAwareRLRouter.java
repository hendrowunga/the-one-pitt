package routing.carl_dtn;

// ... (semua import tetap sama)
import core.*;
import net.sourceforge.jFuzzyLogic.FIS;
import routing.ActiveRouter;
import routing.MessageRouter;
import routing.carl_dtn.context.message.MessageListTable;
import routing.carl_dtn.context.social.Popularity;
import routing.carl_dtn.context.social.TieStrength;
import routing.carl_dtn.fuzzy.FuzzyContextEvaluator;
import routing.carl_dtn.fuzzy.FuzzyMessageEvaluator;
import routing.carl_dtn.learning.QLearningStrategy;
import routing.carl_dtn.learning.QTable;
import routing.carl_dtn.util.ConnectionDuration;
import routing.carl_dtn.util.EncounteredNodeSet;
import routing.carl_dtn.util.NetworkDensityCalculator;

import java.util.*;

public class ContextAwareRLRouter extends ActiveRouter {
    // ... (semua field tetap sama)
    public static final String BUFFER_SIZE = "bufferSize";
    public static final String MSG_TTL = "msgTtl";
    public static final String INIT_ENERGY_S = "initialEnergy";
    public static final String ALPHA_POPULARITY = "alphaPopularity";
    public static final String FCL_CONTEXT = "fcl";
    public static final String FCL_MSG = "fclmsg";
    private static final double NORMAL_PRIORITY_THRESHOLD = 0.4;
    private static final double HIGH_PRIORITY_THRESHOLD = 0.7;
    protected int bufferSize;
    public int msgTtl;
    public int initialEnergy; // Diubah menjadi public
    protected double alphaPopularity;
    protected FIS fclContextEvaluatorFIS;
    protected FIS fclMessageEvaluatorFIS;
    private EncounteredNodeSet encounteredNodeSet;
    private Popularity popularity;
    private TieStrength tieStrength;
    private FuzzyContextEvaluator fuzzyContextEvaluator;
    private FuzzyMessageEvaluator fuzzyMessageEvaluator;
    private QTable qTable;
    private QLearningStrategy qLearningStrategy;
    private MessageListTable messageListTable;
    private Map<String, Double> pendingAging;
    private double latestDensity;
    private Map<DTNHost, ConnectionDuration> connectionHistories;

    public ContextAwareRLRouter(Settings s) {
        super(s);
        // ... (konstruktor tetap sama)
        this.bufferSize = s.getInt(BUFFER_SIZE);
        this.msgTtl = s.getInt(MSG_TTL);
        this.initialEnergy = s.getInt(INIT_ENERGY_S);
        this.alphaPopularity = s.getDouble(ALPHA_POPULARITY);
        this.fclContextEvaluatorFIS = FIS.load(s.getSetting(FCL_CONTEXT), true);
        this.fclMessageEvaluatorFIS = FIS.load(s.getSetting(FCL_MSG), true);
        if (this.fclContextEvaluatorFIS == null || this.fclMessageEvaluatorFIS == null) {
            System.err.println("KRITIS: Gagal memuat file FCL. Pastikan path di file .conf benar.");
        }
        this.encounteredNodeSet = new EncounteredNodeSet();
        this.popularity = new Popularity(alphaPopularity);
        this.tieStrength = new TieStrength();
        this.fuzzyContextEvaluator = new FuzzyContextEvaluator();
        this.fuzzyMessageEvaluator = new FuzzyMessageEvaluator();
        this.pendingAging = new HashMap<>();
        this.latestDensity = 0.0;
        this.connectionHistories = new HashMap<>();
    }

    protected ContextAwareRLRouter(ContextAwareRLRouter r) {
        super(r);
        // ... (konstruktor salinan tetap sama)
        this.bufferSize = r.bufferSize;
        this.msgTtl = r.msgTtl;
        this.initialEnergy = r.initialEnergy;
        this.alphaPopularity = r.alphaPopularity;
        this.fclContextEvaluatorFIS = r.fclContextEvaluatorFIS;
        this.fclMessageEvaluatorFIS = r.fclMessageEvaluatorFIS;
        this.encounteredNodeSet = r.encounteredNodeSet.clone();
        this.popularity = r.popularity;
        this.tieStrength = r.tieStrength;
        this.fuzzyContextEvaluator = r.fuzzyContextEvaluator;
        this.fuzzyMessageEvaluator = r.fuzzyMessageEvaluator;
        this.pendingAging = new HashMap<>(r.pendingAging);
        this.latestDensity = r.latestDensity;
        this.connectionHistories = new HashMap<>(r.connectionHistories);
        this.qTable = null;
        this.qLearningStrategy = null;
        this.messageListTable = null;
    }

    // ... (metode init dan getter tetap sama)
//    @Override
    public void init(DTNHost host, List<MessageListener> mListeners) {
        init(host, mListeners);
        this.qTable = new QTable(String.valueOf(host.getAddress()));
        this.qLearningStrategy = new QLearningStrategy(this.qTable);
        this.messageListTable = new MessageListTable();
    }

    public void postInitQtable(Set<String> allHostIds) {
        if (this.qTable != null) {
            this.qTable.initializeAllQvalues(allHostIds);
        } else {
            System.err.println("Error: QTable null saat postInit pada host " + getHost());
        }
    }

    public FIS getFclContextEvaluatorFIS() { return fclContextEvaluatorFIS; }
    public FIS getFclMessageEvaluatorFIS() { return fclMessageEvaluatorFIS; }
    public QTable getQTable() { return this.qTable; }
    public EncounteredNodeSet getEncounteredNodeSet() { return this.encounteredNodeSet; }
    public Popularity getPopularity() { return this.popularity; }
    public TieStrength getTieStrength() { return this.tieStrength; }

    @Override
    public void changedConnection(Connection con) {
        super.changedConnection(con);
        // PERBAIKAN: Gunakan getHost() yang sekarang sudah public
        DTNHost host = getHost();
        DTNHost neighbor = con.getOtherNode(host);

        if (con.isUp()) {
            handleConnectionUp(host, neighbor);
        } else {
            handleConnectionDown(host, neighbor);
        }
    }

    private void handleConnectionUp(DTNHost host, DTNHost neighbor) {
        long currentTime = (long) SimClock.getTime();
        ContextAwareRLRouter neighborRouter = (ContextAwareRLRouter) neighbor.getRouter();

        double prevDur = connectionHistories.getOrDefault(neighbor, new ConnectionDuration(0)).getTotalDuration();
        connectionHistories.put(neighbor, new ConnectionDuration(prevDur));

        this.encounteredNodeSet.removeOldEncounters();
        neighborRouter.getEncounteredNodeSet().removeOldEncounters();

        this.popularity.updatePopularity(host, this.encounteredNodeSet);
        neighborRouter.getPopularity().updatePopularity(neighbor, neighborRouter.getEncounteredNodeSet());
        this.tieStrength.calculateTieStrength(host, neighbor, this.connectionHistories);

        // PERBAIKAN: Gunakan neighbor.getInitialEnergy() yang baru
        this.encounteredNodeSet.updateENS(host, neighbor, neighbor.toString(), currentTime, neighbor.getInitialEnergy(), neighbor.getRouter().getFreeBufferSize(), this.popularity.getPopularity(neighbor));
        neighborRouter.getEncounteredNodeSet().updateENS(neighbor, host, host.toString(), currentTime, host.getInitialEnergy(), host.getRouter().getFreeBufferSize(), this.popularity.getPopularity(host));

        this.latestDensity = NetworkDensityCalculator.calculateNodeDensity(SimScenario.getInstance().getHosts().size(), this.encounteredNodeSet, neighborRouter.getEncounteredNodeSet());
        this.encounteredNodeSet.exchangeWith(neighborRouter.getEncounteredNodeSet(), host, neighbor, currentTime);
        neighborRouter.getEncounteredNodeSet().exchangeWith(this.encounteredNodeSet, neighbor, host, currentTime);

        double transferOpportunity = fuzzyContextEvaluator.evaluateTransferOpportunity(this, neighbor);
        updateQValueOnConUp(host, neighbor, transferOpportunity);
    }

    // ... (sisa kode di ContextAwareRLRouter tetap sama karena tidak ada error lagi)
    // ... Pastikan Anda menggunakan kode lengkap dari jawaban sebelumnya untuk sisa file ini.
    private void updateQValueOnConUp(DTNHost host, DTNHost neighbor, double transferOpportunity) {
        for (Message msg : getMessageCollection()) {
            String destinationId = String.valueOf(msg.getTo().getAddress());
            if (neighbor.toString().equals(String.valueOf(msg.getFrom().getAddress()))) continue;
            qLearningStrategy.updateFirstStrategy(host, neighbor, destinationId, neighbor.toString(), transferOpportunity);
        }
    }

    private void handleConnectionDown(DTNHost host, DTNHost neighbor) {
        ConnectionDuration currentSession = connectionHistories.get(neighbor);
        if (currentSession != null && currentSession.isActive()) {
            currentSession.endConnection();
        }
        pendingAging.put(neighbor.toString(), SimClock.getTime());
        this.encounteredNodeSet.removeEncounter(neighbor.toString());
        ((ContextAwareRLRouter) neighbor.getRouter()).getEncounteredNodeSet().removeEncounter(host.toString());
    }

    @Override
    public boolean createNewMessage(Message m) {
        makeRoomForMessage(m.getSize());
        int copies = NetworkDensityCalculator.calculateCopiesBasedOnDensity(this.latestDensity);
        m.setTtl(this.msgTtl);
        m.addProperty("copies", copies);
        addToMessages(m, true);
        return true;
    }

    @Override
    protected boolean makeRoomForMessage(int size) {
        if (size > getBufferSize()) return false;
        while (getFreeBufferSize() < size) {
            Message toRemove = getHighestPriorityMessage(true);
            if (toRemove == null) return false;
            deleteMessage(toRemove.getId(), true);
            messageListTable.removeMessage(toRemove);
        }
        return true;
    }

    private Message getHighestPriorityMessage(boolean excludeMsgBeingSent) {
        return getMessageCollection().stream()
                .filter(m -> !excludeMsgBeingSent || !isSending(m.getId()))
                .max(Comparator.comparingDouble(messageListTable::getPriority))
                .orElse(null);
    }

    private void evaluateAllMessagePriorities() {
        for (Message m : getMessageCollection()) {
            double priority = fuzzyMessageEvaluator.evaluateMessagePriority(this, m.getTtl(), m.getHops().size());
            messageListTable.updateMessagePriority(m, priority);
        }
    }

    @Override
    protected Connection tryAllMessagesToAllConnections() {
        List<Connection> connections = getConnections();
        if (connections.isEmpty() || getNrofMessages() == 0) return null;

        List<Message> messages = new ArrayList<>(getMessageCollection());
        messages.sort(Comparator.comparingDouble(messageListTable::getPriority).reversed());

        for (Message msg : messages) {
            for (Connection con : connections) {
                if (con.isReadyForTransfer()) {
                    copiesControlMechanism(msg, con);
                }
            }
        }
        return null;
    }

    private void copiesControlMechanism(Message msg, Connection con) {
        DTNHost host = getHost();
        DTNHost neighbor = con.getOtherNode(host);
        if (neighbor.getRouter().hasMessage(msg.getId()) || neighbor.toString().equals(String.valueOf(msg.getFrom().getAddress()))) {
            return;
        }

        String destinationId = String.valueOf(msg.getTo().getAddress());

        if (neighbor.toString().equals(destinationId)) {
            if (startTransfer(msg, con) == RCV_OK && (int)msg.getProperty("copies") == 1) {
                deleteMessage(msg.getId(), false);
            }
            return;
        }

        double messagePriority = messageListTable.getPriority(msg);
        int copies = (int) msg.getProperty("copies");

        double mySocial = this.tieStrength.getTieStrength(host, neighbor);
        double neighborSocial = ((ContextAwareRLRouter)neighbor.getRouter()).getTieStrength().getTieStrength(neighbor, host);
        double myQ = this.qTable.getQvalue(destinationId, neighbor.toString());
        double neighborQ = ((ContextAwareRLRouter)neighbor.getRouter()).getQTable().getQvalue(destinationId, host.toString());

        boolean socialBetter = neighborSocial > mySocial;
        boolean qValueBetter = neighborQ > myQ;

        if (copies > 1 && messagePriority >= NORMAL_PRIORITY_THRESHOLD && (socialBetter || qValueBetter)) {
            int sendCopies = copies / 2;
            if (sendCopies > 0) {
                Message copy = msg.replicate();
                copy.updateProperty("copies", sendCopies);
                if (startTransfer(copy, con) == RCV_OK) {
                    msg.updateProperty("copies", copies - sendCopies);
                }
            }
        } else if (copies == 1 && messagePriority >= HIGH_PRIORITY_THRESHOLD && (socialBetter && qValueBetter)) {
            if (startTransfer(msg, con) == RCV_OK) {
                deleteMessage(msg.getId(), false);
            }
        }
    }

    @Override
    public void update() {
        super.update();
        if (isTransferring() || !canStartTransfer()) return;

        evaluateAllMessagePriorities();
        if (exchangeDeliverableMessages() != null) return;

        qLearningStrategy.processDelayedAging(getHost(), pendingAging);
        tryAllMessagesToAllConnections();
    }

    @Override
    public Message messageTransferred(String id, DTNHost from) {
        boolean isFirstReception = !hasMessage(id);
        Message msg = super.messageTransferred(id, from);
        if (msg == null || !(from.getRouter() instanceof ContextAwareRLRouter)) return msg;

        boolean isFinalRecipient = msg.getTo() == getHost();

        if (isFinalRecipient || isFirstReception) {
            QTable senderQTable = ((ContextAwareRLRouter) from.getRouter()).getQTable();
            QLearningStrategy.synchronizeQTables(senderQTable, this.qTable);
        }
        return msg;
    }

    @Override
    public ContextAwareRLRouter replicate() {
        return new ContextAwareRLRouter(this);
    }
}