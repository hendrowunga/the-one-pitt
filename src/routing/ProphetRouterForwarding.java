package routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap; // Masih perlu HashMap untuk preds
import java.util.List;
import java.util.Map;
import java.util.Random;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import core.SimError;
import core.Tuple; // Gunakan kelas Tuple

/**
 * Implementation of PRoPHET router as described in
 * <I>Probabilistic routing in intermittently connected networks</I> by
 * Anders Lindgren et al.
 *
 * MODIFIED: Uses List<Tuple> for MOFO/MOPR state tracking (LESS EFFICIENT).
 *
 * © 2025 hendrowunga, University of Sanata Dharma
 */
public class ProphetRouterForwarding extends ActiveRouter {
	/** delivery predictability initialization constant */
	public static final double P_INIT = 0.75;
	/** delivery predictability transitivity scaling constant default value */
	public static final double DEFAULT_BETA = 0.25;
	/** delivery predictability aging constant */
	public static final double GAMMA = 0.98;

	/** Prophet router's setting namespace ({@value}) */
	public static final String PROPHET_NS = "ProphetRouterForwarding";
	/**
	 * Number of seconds in time unit -setting id ({@value}).
	 * How many seconds one time unit is when calculating aging of
	 * delivery predictions. Should be tweaked for the scenario.
	 */
	public static final String SECONDS_IN_UNIT_S = "secondsInTimeUnit";

	/**
	 * Transitivity scaling constant (beta) -setting id ({@value}).
	 * Default value for setting is {@link #DEFAULT_BETA}.
	 */
	public static final String BETA_S = "beta";

	/**
	 * Forwarding strategy -setting id ({@value}). The forwarding strategy
	 * defines how the router should choose the next hop for a message.
	 * Possible values: GRTRMax, GRTRSort, GRTR, COIN.
	 * Default value for setting is GRTRMax.
	 */
	public static final String FORWARDING_STRATEGY_S = "forwardingStrategy";

	/**
	 * Queueing policy -setting id ({@value}). The policy that determines
	 * which message is dropped when the buffer is full.
	 * Possible values: FIFO_DROP, MOFO, SHLI, LEPR, MOPR.
	 * Default value for setting is FIFO_DROP.
	 */
	public static final String QUEUEING_POLICY_S = "queueingPolicy";

	/** the value of nrof seconds in time unit -setting */
	private int secondsInTimeUnit;
	/** value of beta setting */
	private double beta;

	/** delivery predictabilities */
	private Map<DTNHost, Double> preds; // Tetap gunakan Map untuk prediksi DPs

	/** last delivery predictability update (sim)time */
	private double lastAgeUpdate;

	private ForwardingStrategyEnum forwardingStrategyEnum;

	/** The selected queueing policy */
	private QueueingPolicyEnum queueingPolicyEnum;

	/** List of Tuples to store forward counts for MOFO policy */
	private List<Tuple<String, Integer>> forwardedCountsTuple; // MODIFIED

	/**
	 * List of Tuples to store forwarding progress for MOPR policy (FP = sum of
	 * P(B,D))
	 */
	private List<Tuple<String, Double>> forwardProgressesTuple; // MODIFIED

	private Random coinRandom;

	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 *
	 * @param s The settings object
	 */
	public ProphetRouterForwarding(Settings s) {
		super(s);
		Settings prophetSettings = new Settings(PROPHET_NS);
		secondsInTimeUnit = prophetSettings.getInt(SECONDS_IN_UNIT_S);
		if (prophetSettings.contains(BETA_S)) {
			beta = prophetSettings.getDouble(BETA_S);
		} else {
			beta = DEFAULT_BETA;
		}

		// Read and set the forwarding strategy
		if (prophetSettings.contains(FORWARDING_STRATEGY_S)) {
			this.forwardingStrategyEnum = ForwardingStrategyEnum
					.of(prophetSettings.getSetting(FORWARDING_STRATEGY_S));
		} else {
			// Default strategy if not specified
			this.forwardingStrategyEnum = ForwardingStrategyEnum.GRTRMax;
		}

		// Read and set the queueing policy
		if (prophetSettings.contains(QUEUEING_POLICY_S)) {
			this.queueingPolicyEnum = QueueingPolicyEnum.of(prophetSettings.getSetting(QUEUEING_POLICY_S));
		} else {
			this.queueingPolicyEnum = QueueingPolicyEnum.FIFO_DROP; // Default policy
		}
		initPolicyMaps();

		initPreds();
	}

	/**
	 * Copyconstructor.
	 *
	 * @param r The router prototype where setting values are copied from
	 */
	protected ProphetRouterForwarding(ProphetRouterForwarding r) {
		super(r);
		this.secondsInTimeUnit = r.secondsInTimeUnit;
		this.forwardingStrategyEnum = r.forwardingStrategyEnum;
		this.beta = r.beta;
		this.queueingPolicyEnum = r.queueingPolicyEnum;

		initPreds();
		// Copy state for policies - Requires deep copy of tuples
		initPolicyMaps();
		for (Tuple<String, Integer> t : r.forwardedCountsTuple) {
			this.forwardedCountsTuple.add(new Tuple<>(t.getKey(), t.getValue())); // Deep copy tuple
		}
		for (Tuple<String, Double> t : r.forwardProgressesTuple) {
			this.forwardProgressesTuple.add(new Tuple<>(t.getKey(), t.getValue())); // Deep copy tuple
		}

	}

	/**
	 * Initializes predictability hash
	 */
	private void initPreds() {
		this.preds = new HashMap<DTNHost, Double>();
		this.lastAgeUpdate = SimClock.getTime(); // Initialize lastAgeUpdate
		this.coinRandom = new Random(SimClock.getIntTime()); // Initialize random for COIN
	}

	/**
	 * Initializes policy maps (forwardedCountsTuple, forwardProgressesTuple).
	 */
	private void initPolicyMaps() {
		this.forwardedCountsTuple = new ArrayList<>(); // MODIFIED
		this.forwardProgressesTuple = new ArrayList<>(); // MODIFIED
	}

	@Override
	public void changedConnection(Connection con) {
		if (con.isUp()) {
			DTNHost otherHost = con.getOtherNode(getHost());
			updateDeliveryPredFor(otherHost);
			updateTransitivePreds(otherHost);
		}
	}

	/**
	 * Updates delivery predictions for a host.
	 * <CODE>P(a,b) = P(a,b)_old + (1 - P(a,b)_old) * P_INIT</CODE>
	 *
	 * @param host The host we just met
	 */
	private void updateDeliveryPredFor(DTNHost host) {
		double oldValue = getPredFor(host);
		double newValue = oldValue + (1 - oldValue) * P_INIT;
		preds.put(host, newValue);
	}

	/**
	 * Returns the current prediction (P) value for a host or 0 if entry for
	 * the host doesn't exist.
	 *
	 * @param host The host to look the P for
	 * @return the current P value
	 */
	public double getPredFor(DTNHost host) {
		ageDeliveryPreds(); // make sure preds are updated before getting
		if (preds.containsKey(host)) {
			return preds.get(host);
		} else {
			return 0;
		}
	}

	/**
	 * Updates transitive (A->B->C) delivery predictions.
	 * <CODE>P(a,c) = P(a,c)_old + (1 - P(a,c)_old) * P(a,b) * P(b,c) * BETA
	 * </CODE>
	 *
	 * @param host The B host who we just met
	 */
	private void updateTransitivePreds(DTNHost host) {
		MessageRouter otherRouter = host.getRouter();
		// Ensure the other router is also ProphetRouterForwarding
		if (!(otherRouter instanceof ProphetRouterForwarding)) {
			// This can happen if simulating a network with mixed router types.
			// If Prophet needs to work with only Prophet, an assert is fine.
			// If it needs to interoperate (perhaps with limited functionality), handle it.
			// Paper implies full Prophet interop, so assert might be intended.
			// However, other examples might need graceful handling. Let's add a check.
			// assert otherRouter instanceof ProphetRouterForwarding : "PRoPHET only
			// works " +
			// " with other routers of same type";
			if (!(otherRouter instanceof ProphetRouterForwarding)) {
				// Cannot get Prophet predictions from non-Prophet router for transitivity
				return;
			}
		}

		ProphetRouterForwarding othProphetRouter = (ProphetRouterForwarding) otherRouter;

		double pForHost = getPredFor(host); // P(a,b)
		Map<DTNHost, Double> othersPreds = othProphetRouter.getDeliveryPreds();

		for (Map.Entry<DTNHost, Double> e : othersPreds.entrySet()) {
			if (e.getKey() == getHost()) {
				continue; // don't add yourself
			}

			double pOld = getPredFor(e.getKey()); // P(a,c)_old
			double pNew = pOld + (1 - pOld) * pForHost * e.getValue() * beta;
			preds.put(e.getKey(), pNew);
		}
	}

	/**
	 * Ages all entries in the delivery predictions.
	 * <CODE>P(a,b) = P(a,b)_old * (GAMMA ^ k)</CODE>, where k is number of
	 * time units that have elapsed since the last time the metric was aged.
	 *
	 * @see #SECONDS_IN_UNIT_S
	 */
	private void ageDeliveryPreds() {
		double timeDiff = (SimClock.getTime() - this.lastAgeUpdate) /
				secondsInTimeUnit;

		if (timeDiff <= 0) { // Handle timeDiff = 0 or negative (shouldn't happen with positive clock
							// increments, but for safety)
			return;
		}

		double mult = Math.pow(GAMMA, timeDiff);
		// Use Iterator to safely remove expired predictions if necessary (though not in
		// Prophet paper spec)
		// Or simply update existing ones as per spec
		for (Map.Entry<DTNHost, Double> e : preds.entrySet()) {
			e.setValue(e.getValue() * mult);
		}

		this.lastAgeUpdate = SimClock.getTime();
	}

	/**
	 * Returns a map of this router's delivery predictions
	 *
	 * @return a map of this router's delivery predictions
	 */
	private Map<DTNHost, Double> getDeliveryPreds() {
		ageDeliveryPreds(); // make sure the aging is done
		return this.preds;
	}

	@Override
	public void update() {
		super.update();
		if (!canStartTransfer() || isTransferring()) {
			return; // nothing to transfer or is currently transferring
		}

		// try messages that could be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return;
		}
		// If no deliverable message, try other messages based on forwarding strategy
		tryOtherMessages();
	}

	/**
	 * Tries to send all other messages to all connected hosts ordered by
	 * their delivery probability
	 *
	 * @return The return value of {@link #tryMessagesForConnected(List)}
	 */
	private Tuple<Message, Connection> tryOtherMessages() {
		List<Tuple<Message, Connection>> messages = new ArrayList<Tuple<Message, Connection>>();

		Collection<Message> msgCollection = getMessageCollection();

		/*
		 * for all connected hosts collect all messages that have a higher
		 * probability of delivery by the other host
		 */
		// for (Connection con : getHost()) { // Use getHost().getConnections() or
		// getConnections()
		for (Connection con : getConnections()) {
			DTNHost other = con.getOtherNode(getHost());
			ProphetRouterForwarding othRouter = (ProphetRouterForwarding) other.getRouter();

			// Ensure the oher router is also ProphetRouterForwarding,unless the strategy
			// is COIN
			if (forwardingStrategyEnum != ForwardingStrategyEnum.COIN
					&& !(othRouter instanceof ProphetRouterForwarding)) {
				// Cannot apply Prophet forwarding filter to non-Prophet router
				continue;
			}

			if (othRouter.isTransferring()) { // Use base router type for generic check
				continue; // skip hosts that are transferring
			}

			ProphetRouterForwarding othProphetRouter = (forwardingStrategyEnum != ForwardingStrategyEnum.COIN)
					? (ProphetRouterForwarding) othRouter
					: null;

			for (Message m : msgCollection) {
				if (othRouter.hasMessage(m.getId())) { // Use base router type for generic check
					continue; // skip messages that the other one has
				}
				boolean shouldConsider = false;
				switch (forwardingStrategyEnum) {
					case COIN:
						// Ensure coinRandom is initialized before use (belt-and-suspenders)
						if (this.coinRandom == null) {
							this.coinRandom = new Random(SimClock.getIntTime());
						}
						if (this.coinRandom.nextDouble() > 0.5) { // X > 0.5
							shouldConsider = true;
						}
						break;
					case GRTR: // Fall-through intentional
					case GRTRSort: // Fall-through intentional
					case GRTRMax:
						// For GRTR-based strategies, apply the P(B,D) > P(A,D) filter
						// othProphetRouter is guaranteed to be non-null here by the outer check
						assert othProphetRouter != null
								: "othRouter should be ProphetRouterForwarding for GRTR strategies";
						if (othProphetRouter.getPredFor(m.getTo()) > getPredFor(m.getTo())) {
							shouldConsider = true;
						}
						break;
					default:
						// Should not happen with current enums, but good practice
						throw new SimError("Unknown forwarding strategy: " + forwardingStrategyEnum);
				}
				if (shouldConsider) {
					messages.add(new Tuple<Message, Connection>(m, con));
				}
			}
		}

		if (messages.size() == 0) {
			return null;
		}

		// --- Sort the list of potential transfers based on strategy (if required) ---
		switch (forwardingStrategyEnum) {
			case COIN:
			case GRTR:
				// NO SORTING for COIN or GRTR
				break;
			case GRTRSort:
				// Sort by P(B,D) - P(A,D) difference (descending)
				Collections.sort(messages, new GRTRSortTupleComparator());
				break;
			case GRTRMax:
				// Sort by P(B,D) probability (descending)
				Collections.sort(messages, new GRTRMaxTupleComparator());
				break;
		}

		// Try to send the messages in the determined order
		return tryMessagesForConnected(messages);
	}

	@Override
	public RoutingInfo getRoutingInfo() {
		ageDeliveryPreds();
		RoutingInfo top = super.getRoutingInfo();
		RoutingInfo ri = new RoutingInfo(preds.size() +
				" delivery prediction(s), strategy: " + forwardingStrategyEnum + ", policy: " + queueingPolicyEnum);

		for (Map.Entry<DTNHost, Double> e : preds.entrySet()) {
			DTNHost host = e.getKey();
			Double value = e.getValue();

			ri.addMoreInfo(new RoutingInfo(String.format("%s : %.6f",
					host, value)));
		}
		// Tambahkan info MOFO/MOPR jika aktif, menggunakan size list Tuple
		if (queueingPolicyEnum == QueueingPolicyEnum.MOFO) {
			RoutingInfo mofoInfo = new RoutingInfo(
					forwardedCountsTuple.size() + " msgs with forward counts (Tuple List)"); // MODIFIED size()
																				// call
			ri.addMoreInfo(mofoInfo);
		}
		if (queueingPolicyEnum == QueueingPolicyEnum.MOPR) {
			RoutingInfo moprInfo = new RoutingInfo(
					forwardProgressesTuple.size() + " msgs with forward progress (Tuple List)"); // MODIFIED size()
																					// call
			ri.addMoreInfo(moprInfo);
		}

		top.addMoreInfo(ri);
		return top;
	}

	@Override
	public MessageRouter replicate() {
		ProphetRouterForwarding r = new ProphetRouterForwarding(this);
		return r;
	}

	public enum ForwardingStrategyEnum {
		GRTRMax("GRTRMax"),
		GRTRSort("GRTRSort"),
		GRTR("GRTR"),
		COIN("COIN");

		private String name;

		private ForwardingStrategyEnum(String name) {
			this.name = name;
		}

		/**
		 * Returns the enum constant for the given strategy name string.
		 *
		 * @param name The name of the strategy (case-insensitive)
		 * @return The corresponding enum constant
		 * @throws IllegalArgumentException if the name is unknown
		 */
		public static ForwardingStrategyEnum of(String name) {
			for (ForwardingStrategyEnum strategy : values()) {
				if (strategy.name.equalsIgnoreCase(name)) {
					return strategy;
				}
			}
			throw new IllegalArgumentException("Unknown forwarding strategy: " + name);
		}

		@Override
		public String toString() {
			return name;
		}
	}

	/**
	 * (GRTRMax) Comparator for Message-Connection-Tuples that orders the tuples
	 * by their delivery probability by the host on the other side of the
	 * connection (descending). Uses queue mode as tie-breaker.
	 */
	private class GRTRMaxTupleComparator implements Comparator<Tuple<Message, Connection>> {
		@Override
		public int compare(Tuple<Message, Connection> tuple1,
				Tuple<Message, Connection> tuple2) {

			// delivery probability of tuple1's message with tuple1's connection
			double p1 = ((ProphetRouterForwarding) tuple1.getValue().getOtherNode(getHost()).getRouter())
					.getPredFor(
							tuple1.getKey().getTo());
			// -"- tuple2...
			double p2 = ((ProphetRouterForwarding) tuple2.getValue().getOtherNode(getHost()).getRouter())
					.getPredFor(
							tuple2.getKey().getTo());

			// Sort descending by probability (p2 - p1)
			if (p2 - p1 == 0) {
				/* equal probabilities -> let queue mode decide as a tie-breaker */
				return compareByQueueMode(tuple1.getKey(), tuple2.getKey());

			} else if (p2 - p1 < 0) {
				return -1; // p1 > p2, tuple1 comes after tuple2
			} else {
				return 1; // p2 > p1, tuple2 comes after tuple1
			}
		}
	}

	/**
	 * (GRTRSort) Comparator for Message-Connection-Tuples that orders the tuples
	 * by the difference P(B,D) - P(A,D) (descending). Uses queue mode as
	 * tie-breaker.
	 */
	private class GRTRSortTupleComparator implements Comparator<Tuple<Message, Connection>> {
		@Override
		public int compare(Tuple<Message, Connection> tuple1,
				Tuple<Message, Connection> tuple2) {

			// Note: othProphetRouterForwarding is guaranteed non-null when this comparator
			// is used (GRTRMax/GRTRSort)

			// Mendapatkan router peer untuk kedua tuple
			ProphetRouterForwarding otherRouter1 = (ProphetRouterForwarding) tuple1.getValue()
					.getOtherNode(getHost()).getRouter(); // --- MODIFIED (Cast here) ---
			ProphetRouterForwarding otherRouter2 = (ProphetRouterForwarding) tuple2.getValue()
					.getOtherNode(getHost()).getRouter(); // --- MODIFIED (Cast here) ---

			// Menghitung P(B,D) dan P(A,D) untuk pesan pertama (tuple1)
			double pBD1 = otherRouter1.getPredFor(tuple1.getKey().getTo()); // P(B1, D1)
			double pAD1 = getPredFor(tuple1.getKey().getTo()); // P(A, D1) - Prediktabilitas node saat ini (A) ke
														// tujuan pesan 1 (D1)

			// Menghitung P(B,D) dan P(A,D) untuk pesan kedua (tuple2)
			double pBD2 = otherRouter2.getPredFor(tuple2.getKey().getTo()); // P(B2, D2)
			double pAD2 = getPredFor(tuple2.getKey().getTo()); // P(A, D2) - Prediktabilitas node saat ini (A) ke
														// tujuan pesan 2 (D2)

			// Menghitung selisih P(B,D) - P(A,D) untuk kedua pesan
			double difference1 = pBD1 - pAD1;
			double difference2 = pBD2 - pAD2;

			// Mengurutkan menurun berdasarkan selisih (difference2 - difference1)
			if (difference2 - difference1 == 0) {
				/* equal differences -> let queue mode decide as a tie-breaker */
				return compareByQueueMode(tuple1.getKey(), tuple2.getKey());
			} else if (difference2 - difference1 < 0) {
				return -1; // difference1 > difference2, tuple1 comes setelah tuple2
			} else {
				return 1; // difference2 > difference1, tuple2 comes setelah tuple1
			}
		}
	}

	public enum QueueingPolicyEnum {
		FIFO_DROP("FIFO_DROP"), // Drop terlama (standard di ActiveRouter saat ini)
		MOFO("MOFO"), // Drop paling sering diforward
		SHLI("SHLI"), // Drop TTL tersingkat
		LEPR("LEPR"), // Drop P terendah (dari node saat ini ke tujuan)
		MOPR("MOPR"); // Drop Forwarding Progress tertinggi

		private final String name;

		QueueingPolicyEnum(String name) {
			this.name = name;
		}

		/**
		 * Returns the enum constant for the given policy name string.
		 *
		 * @param name The name of the policy (case-insensitive)
		 * @return The corresponding enum constant
		 * @throws IllegalArgumentException if the name is unknown
		 */
		public static QueueingPolicyEnum of(String name) {
			for (QueueingPolicyEnum policy : values()) {
				if (policy.name.equalsIgnoreCase(name)) {
					return policy;
				}
			}
			throw new IllegalArgumentException("Unknown queueing policy: " + name);
		}

		@Override
		public String toString() {
			return name;
		}
	}

	@Override
	public void deleteMessage(String id, boolean drop) {
		// MODIFIED: Remove from Tuple lists
		removeTupleByMessageId(forwardedCountsTuple, id);
		removeTupleByMessageId(forwardProgressesTuple, id);

		super.deleteMessage(id, drop); // Call superclass to delete message from buffer etc.
	}

	/** Helper to find and remove a tuple by message ID from a list of tuples */
	private <V> void removeTupleByMessageId(List<Tuple<String, V>> tupleList, String msgId) {
		for (int i = 0; i < tupleList.size(); i++) {
			if (tupleList.get(i).getKey().equals(msgId)) {
				tupleList.remove(i);
				return; // Assuming only one tuple per message ID
			}
		}
	}

	@Override
	protected void transferDone(Connection con) {
		super.transferDone(con);

		// Logika pembaruan state MOFO/MOPR hanya berjalan di sisi pengirim
		// Menggunakan getHost() dan getOtherNode() karena Connection tidak punya method
		// getSender/Receiver
		// Asumsikan router ini adalah pengirim karena transferDone dipanggil untuk
		// koneksi di sendingConnections
		DTNHost sender = getHost();
		// Penerima adalah node lain di koneksi ini
		DTNHost receiver = con.getOtherNode(sender);

		Message transferredMsg = con.getMessage();
		if (transferredMsg == null) {
			return; // Should not happen if transfer was done
		}
		String msgId = transferredMsg.getId();

		// --- Update MOFO (Forward Count) ---
		// MODIFIED: Find and update tuple in list
		int currentCount = getForwardedCountForMessage(msgId, 0);
		updateForwardedCountForMessage(msgId, currentCount + 1);

		// --- Update MOPR (Forwarding Progress) ---
		// MODIFIED: Find and update tuple in list
		double pBD = 0.0;
		// Get P(B,D) from receiver if it's a ProphetRouterForwarding
		MessageRouter receiverRouterBase = receiver.getRouter();
		if (receiverRouterBase instanceof ProphetRouterForwarding) {
			ProphetRouterForwarding receiverRouter = (ProphetRouterForwarding) receiverRouterBase;
			pBD = receiverRouter.getPredFor(transferredMsg.getTo()); // Dapatkan P(B,D)
		}

		double currentFP = getForwardProgressForMessage(msgId, 0.0);
		updateForwardProgressForMessage(msgId, currentFP + pBD);
	}

	/** Helper to find forwarded count for a message ID from the tuple list */
	private int getForwardedCountForMessage(String msgId, int defaultValue) {
		for (Tuple<String, Integer> tuple : forwardedCountsTuple) {
			if (tuple.getKey().equals(msgId)) {
				return tuple.getValue();
			}
		}
		return defaultValue;
	}

	/** Helper to update or add forwarded count tuple for a message ID */
	private void updateForwardedCountForMessage(String msgId, int newCount) {
		// Remove existing tuple if any
		removeTupleByMessageId(forwardedCountsTuple, msgId);
		// Add new tuple
		forwardedCountsTuple.add(new Tuple<>(msgId, newCount));
	}

	/** Helper to find forwarding progress for a message ID from the tuple list */
	private double getForwardProgressForMessage(String msgId, double defaultValue) {
		for (Tuple<String, Double> tuple : forwardProgressesTuple) {
			if (tuple.getKey().equals(msgId)) {
				return tuple.getValue();
			}
		}
		return defaultValue;
	}

	/** Helper to update or add forwarding progress tuple for a message ID */
	private void updateForwardProgressForMessage(String msgId, double newFP) {
		// Remove existing tuple if any
		removeTupleByMessageId(forwardProgressesTuple, msgId);
		// Add new tuple
		forwardProgressesTuple.add(new Tuple<>(msgId, newFP));
	}

	@Override
	public Message messageTransferred(String id, DTNHost from) {
		// This method is called on the receiving host after a transfer.
		// MOFO/MOPR state updates happen on the sending host (in transferDone).
		// So, we only call the superclass method here.
		Message m = super.messageTransferred(id, from);
		return m;
	}

	// --- START: Implementasi Queueing Policy (Drop Logic) --- //

	@Override // Override makeRoomForMessage dari ActiveRouter
	protected boolean makeRoomForMessage(int size) {
		if (size > this.getBufferSize()) {
			return false; // message too big for the buffer
		}

		int freeBuffer = this.getFreeBufferSize();
		/* delete messages from the buffer until there's enough space */
		while (freeBuffer < size) {
			// Panggil metode untuk memilih pesan yang akan di-drop berdasarkan kebijakan
			Message m = selectMessageToDrop(true); // true: jangan drop pesan yang sedang dikirim

			if (m == null) {
				// Tidak ada pesan yang bisa di-drop sama sekali dan buffer masih penuh.
				// Ini bisa terjadi jika semua pesan sedang dikirim dan buffer penuh.
				// Dalam kasus ini, tidak ada ruang yang bisa dibuat saat ini.
				return false;
			}

			/* delete message from the buffer as "drop" */
			deleteMessage(m.getId(), true); // Memanggil deleteMessage yang sudah di-override untuk clean up map
			freeBuffer += m.getSize();
		}
		return true;
	}

	/**
	 * Memilih pesan dari buffer untuk di-drop berdasarkan kebijakan antrian.
	 * Menggunakan compareByQueueMode sebagai tie-breaker sekunder.
	 *
	 * @param excludeMsgBeingSent Jangan pilih pesan yang sedang dikirim jika true.
	 * @return Pesan yang dipilih untuk di-drop atau null jika tidak ada.
	 */
	protected Message selectMessageToDrop(boolean excludeMsgBeingSent) {
		Collection<Message> messages = this.getMessageCollection();
		Message messageToDrop = null;

		// Buat daftar pesan yang bisa di-drop (tidak sedang dikirim jika
		// excludeMsgBeingSent true)
		List<Message> droppableMessages = new ArrayList<>();
		for (Message m : messages) {
			if (!(excludeMsgBeingSent && isSending(m.getId()))) { // isSending() from ActiveRouter
				droppableMessages.add(m);
			}
		}

		if (droppableMessages.isEmpty()) {
			return null; // Tidak ada pesan yang bisa di-drop
		}

		// --- Menggunakan Comparator Komposit untuk Menentukan Pesan yang Akan di-Drop
		// ---
		// Comparator ini akan membuat pesan yang harus di-drop pertama menjadi yang
		// "tertinggi"
		// berdasarkan kriteria kebijakan dan tie-breaker, sehingga kita bisa pakai
		// Collections.max.
		Comparator<Message> dropComparator = null;

		// Tie-breaker sekunder: gunakan compareByQueueMode dari superclass
		Comparator<Message> secondaryTieBreaker = this::compareByQueueMode; // compareByQueueMode is from
																// MessageRouter

		switch (queueingPolicyEnum) {
			case FIFO_DROP:
				// Kebijakan: Drop pesan paling tua (receiveTime terkecil)
				// Prioritas Drop TERTINGGI = receiveTime TERKECIL
				// Comparator.comparingDouble(rx) defaultnya: kecil < besar (ascending)
				// Kita ingin yang TERKECIL receiveTime-nya menjadi yang TERBESAR untuk
				// Collections.max
				dropComparator = Comparator.comparingDouble(Message::getReceiveTime)
						.thenComparing(secondaryTieBreaker) // Tie-breaker
						.reversed(); // Membalik urutan: yang terkecil jadi terbesar
				break;

			case MOFO:
				// Kebijakan: Drop pesan yang paling sering diforward (forwardedCounts
				// tertinggi)
				// Prioritas Drop TERTINGGI = forwardedCounts TERBESAR
				// Comparator.comparingInt(count) defaultnya: kecil < besar (ascending)
				// Kita ingin yang TERBESAR count-nya menjadi yang TERBESAR untuk
				// Collections.max
				// Mengambil count menggunakan helper method
				dropComparator = Comparator.comparingInt((Message m) -> getForwardedCountForMessage(m.getId(), 0))
						.thenComparing(secondaryTieBreaker); // Tie-breaker. Count tertinggi akan dianggap
														// "terbesar" oleh comparator ini.
				break;

			case SHLI:
				// Kebijakan: Drop pesan dengan sisa TTL tersingkat (TTL terkecil)
				// Prioritas Drop TERTINGGI = TTL TERKECIL
				// Comparator.comparingInt(ttl) defaultnya: kecil < besar (ascending)
				// Kita ingin yang TERKECIL TTL-nya menjadi yang TERBESAR untuk Collections.max
				dropComparator = Comparator.comparingInt(Message::getTtl)
						.thenComparing(secondaryTieBreaker) // Tie-breaker
						.reversed(); // Membalik urutan: yang terkecil jadi terbesar
				break;

			case LEPR:
				// Kebijakan: Drop pesan dengan prediksi pengiriman (P(A,D)) terendah
				// Prioritas Drop TERTINGGI = P(A,D) TERKECIL
				// Comparator.comparingDouble(P) defaultnya: kecil < besar (ascending)
				// Kita ingin yang TERKECIL P-nya menjadi yang TERBESAR untuk Collections.max
				// Penggunaan comparingDouble secara otomatis menangani perbandingan double
				// secara robust.
				dropComparator = Comparator.comparingDouble((Message m) -> getPredFor(m.getTo())) // getPredFor dari
																					// kelas ini
						.thenComparing(secondaryTieBreaker) // Tie-breaker
						.reversed(); // Membalik urutan: yang terkecil jadi terbesar
				break;

			case MOPR:
				// Kebijakan: Drop pesan dengan forward progress (FP) tertinggi
				// Prioritas Drop TERTINGGI = FP TERBESAR
				// Comparator.comparingDouble(FP) defaultnya: kecil < besar (ascending)
				// Kita ingin yang TERBESAR FP-nya menjadi yang TERBESAR untuk Collections.max
				// Mengambil FP menggunakan helper method
				dropComparator = Comparator
						.comparingDouble((Message m) -> getForwardProgressForMessage(m.getId(), 0.0))
						.thenComparing(secondaryTieBreaker); // Tie-breaker. FP tertinggi akan dianggap "terbesar"
														// oleh comparator ini.
				break;

			default:
				// Seharusnya tidak terjadi jika kebijakan antrian dipilih dengan benar dari
				// enum.
				throw new SimError("Unknown queueing policy " + queueingPolicyEnum + " in selectMessageToDrop");
		}

		// Cari pesan dengan prioritas dropping tertinggi menggunakan Collections.max
		// Comparator di atas sudah disusun sedemikian rupa sehingga pesan yang ingin
		// di-drop pertama kali
		// akan dianggap sebagai elemen "terbesar" oleh comparator.
		messageToDrop = Collections.max(droppableMessages, dropComparator);

		// Pengecekan keamanan (seharusnya tidak null jika droppableMessages tidak
		// kosong)
		if (messageToDrop == null && !droppableMessages.isEmpty()) {
			throw new SimError("Queueing policy " + queueingPolicyEnum
					+ " failed to select a message to drop from a non-empty droppable list (Collections.max returned null).");
		}

		return messageToDrop; // Kembalikan pesan yang akan di-drop (atau null jika list kosong)
	}
	// --- END: Implementasi Queueing Policy (Drop Logic) --- //

}