package routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import core.SimError;
import core.Tuple;

/**
 * Implementation of PRoPHET router as described in
 * <I>Probabilistic routing in intermittently connected networks</I> by
 * Anders Lindgren et al.
 *
 * © 2025 hendrowunga, University of Sanata Dharma
 */
public class ProphetRouterForwardingMod extends ActiveRouter {
	/** delivery predictability initialization constant */
	public static final double P_INIT = 0.75;
	/** delivery predictability transitivity scaling constant default value */
	public static final double DEFAULT_BETA = 0.25;
	/** delivery predictability aging constant */
	public static final double GAMMA = 0.98;

	/** Prophet router's setting namespace ({@value}) */
	public static final String PROPHET_NS = "ProphetRouterForwardingMod";
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
	private Map<DTNHost, Double> preds;
	/** last delivery predictability update (sim)time */
	private double lastAgeUpdate;

	private ForwardingStrategyEnum forwardingStrategyEnum;

	/** The selected queueing policy */
	private QueueingPolicyEnum queueingPolicyEnum;

	/** Map to store forward counts for MOFO policy */
	private Map<String, Integer> forwardedCounts;

	/** Map to store forwarding progress for MOPR policy (FP = sum of P(B,D)) */
	private Map<String, Double> forwardProgresses;

	private Random coinRandom;

	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * 
	 * @param s The settings object
	 */
	public ProphetRouterForwardingMod(Settings s) {
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
	protected ProphetRouterForwardingMod(ProphetRouterForwardingMod r) {
		super(r);
		this.secondsInTimeUnit = r.secondsInTimeUnit;
		this.forwardingStrategyEnum = r.forwardingStrategyEnum;
		// this.lastAgeUpdate = r.lastAgeUpdate;
		this.beta = r.beta;
		this.queueingPolicyEnum = r.queueingPolicyEnum;

		initPreds();
		initPolicyMaps();
	}

	/**
	 * Initializes predictability hash
	 */
	private void initPreds() {
		this.preds = new HashMap<DTNHost, Double>();
	}

	/**
	 * Initializes policy maps (forwardedCounts, forwardProgresses). - METODE BARU -
	 * DITAMBAHKAN
	 * Ini hanya menginisialisasi map kosong.
	 */
	private void initPolicyMaps() {
		this.forwardedCounts = new HashMap<>();
		this.forwardProgresses = new HashMap<>();
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
		// Memastikan node yang ditemui juga menggunakan router PRoPHET (kecuali untuk COIN/EPIDEMIC yang tidak butuh P)
		if (!(otherRouter instanceof ProphetRouterForwardingMod) && forwardingStrategyEnum != ForwardingStrategyEnum.COIN && forwardingStrategyEnum != ForwardingStrategyEnum.EPIDEMIC) {
			return;
		}

		double pForHost = getPredFor(host); // P(a,b) - Prediksi dari node saat ini (A) ke node yang ditemui (B)
		Map<DTNHost, Double> othersPreds = ((ProphetRouterForwardingMod) otherRouter).getDeliveryPreds(); // Mendapatkan prediksi B ke C (P(b,c)) dari node B

		for (Map.Entry<DTNHost, Double> e : othersPreds.entrySet()) {
			if (e.getKey() == getHost()) { // Host C (tujuan/getKey()) and P(b,c) - Prediksi dari node B ke host C (getHost())
				continue; // Jangan update prediksi dari A ke A melalui B
			}

			double pOld = getPredFor(e.getKey()); // P(a,c)_old - Prediksi saat ini (A) ke host C
			double pNew = pOld + (1 - pOld) * pForHost * e.getValue() * beta;
			preds.put(e.getKey(), pNew);// Simpan prediksi A ke C yang baru
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

		if (timeDiff == 0) { // Also handle timeDiff = 0 or negative (shouldn't happen but for safety)
			return;
		}

		double mult = Math.pow(GAMMA, timeDiff);
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
		// for (Connection con : getHost()) {
		for (Connection con : getConnections()) {
			DTNHost other = con.getOtherNode(getHost());
			ProphetRouterForwardingMod othRouter = (ProphetRouterForwardingMod) other.getRouter();

			// Ensure the oher router is also ProphetRouterForwardingMod,unless the strategy
			// is
			// coin
			if (forwardingStrategyEnum != ForwardingStrategyEnum.COIN
					&& !(othRouter instanceof ProphetRouterForwardingMod)) {
				// System.err.println("Warning: Skipping non-Prophet router for non-COIN
				// strategy.");
				continue;
			}
			ProphetRouterForwardingMod othProphetRouterForwardingMod = null;
			if (othRouter instanceof ProphetRouterForwardingMod) {
				othProphetRouterForwardingMod = (ProphetRouterForwardingMod) othRouter;
			}

			if (othRouter.isTransferring()) {
				continue; // skip hosts that are transferring
			}

			// Prophet-based strategies (GRTR, GRTRSort, GRTRMax) require the peer to be
			// Prophet
			// COIN and EPIDEMIC strategies do not require the peer to be Prophet
			boolean requiresProphetPeer = (forwardingStrategyEnum == ForwardingStrategyEnum.GRTR ||
					forwardingStrategyEnum == ForwardingStrategyEnum.GRTRSort ||
					forwardingStrategyEnum == ForwardingStrategyEnum.GRTRMax);

			if (requiresProphetPeer && !(othRouter instanceof ProphetRouterForwardingMod)) {
				// Skip peer if Prophet-based strategy requires it but peer is not Prophet
				continue;
			}

			for (Message m : msgCollection) {
				if (othRouter.hasMessage(m.getId())) {
					continue; // skip messages that the other one has
				}
				boolean shouldConsider = false;
				switch (forwardingStrategyEnum) {

					case EPIDEMIC:
						// Strategy EPIDEMIC: Forward SEMUA pesan yang belum dimiliki peer (Flood)
						shouldConsider = true;
						break;
					case COIN:
						// Strategy COIN: Forward berdasarkan hasil 'coin toss' (probabilitas 0.5)
						// Ini adalah PENDINGKASAN dari logic COIN sebenarnya di paper (X > 0.5 random variable),
						// Kode ini menggunakan random.nextDouble() > 0.5
						if (this.coinRandom == null) {
							// This indicates a potential initialization issue.
							// Re-initialize as a fallback to prevent crash.
							// System.err.println(
							// "WARNING: coinRandom was NULL for COIN strategy! Re-initializing in
							// tryOtherMessages for router "
							// + getHost() + "."); // Add host info
							this.coinRandom = new Random(SimClock.getIntTime()); // Re-initialize with SimClock
																		// time
						}
						if (this.coinRandom.nextDouble() > 0.5) { // X > 0.5
							shouldConsider = true;
						}
						break;
					case GRTR: // Strategy GRTR: Only forward if P(B,D) > P(A,D)
					case GRTRSort: // Strategy GRTRSort: Filter dulu dengan P(B,D) > P(A,D)
					case GRTRMax:
						// Strategy GRTRMax: Filter dulu dengan P(B,D) > P(A,D)
						// Untuk strategi berbasis GRTR, cek apakah prediksi peer (B) ke tujuan (D)
						// lebih tinggi dari prediksi node saat ini (A) ke tujuan (D)
						ProphetRouterForwardingMod othProphetRouter = (ProphetRouterForwardingMod) other.getRouter(); // Peer harus Prophet
						if (othProphetRouter.getPredFor(m.getTo()) > getPredFor(m.getTo())) { // Cek P(B,D) > P(A,D)
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
			case EPIDEMIC:
			case COIN:
			case GRTR:
				// Untuk EPIDEMIC, COIN, GRTR (dasar): Tidak ada sorting tambahan.
				// Order ditentukan oleh urutan iterasi pesan atau hasil coin toss.
				break;
			case GRTRSort:
				// Strategy GRTRSort: Sort pesan berdasarkan selisih P(B,D) - P(A,D)  (descending)
				Collections.sort(messages, new GRTRSortTupleComparator());
				break;
			case GRTRMax:
				// Strategy GRTRMax: Sort pesan berdasarkan P(B,D) (descending)
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
		// Tambahkan info MOFO/MOPR jika aktif
		if (queueingPolicyEnum == QueueingPolicyEnum.MOFO) {
			RoutingInfo mofoInfo = new RoutingInfo(forwardedCounts.size() + " msgs with forward counts");
			ri.addMoreInfo(mofoInfo);
		}
		if (queueingPolicyEnum == QueueingPolicyEnum.MOPR) {
			RoutingInfo moprInfo = new RoutingInfo(forwardProgresses.size() + " msgs with forward progress");
			ri.addMoreInfo(moprInfo);
		}

		top.addMoreInfo(ri);
		return top;
	}

	@Override
	public MessageRouter replicate() {
		ProphetRouterForwardingMod r = new ProphetRouterForwardingMod(this);
		return r;
	}

	public enum ForwardingStrategyEnum {
		GRTRMax("GRTRMax"),
		GRTRSort("GRTRSort"),
		GRTR("GRTR"),
		COIN("COIN"),
		EPIDEMIC("EPIDEMIC");

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
			double p1 = ((ProphetRouterForwardingMod) tuple1.getValue().getOtherNode(getHost()).getRouter())
					.getPredFor(
							tuple1.getKey().getTo());
			// -"- tuple2...
			double p2 = ((ProphetRouterForwardingMod) tuple2.getValue().getOtherNode(getHost()).getRouter())
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
	private class GRTRSortTupleComparator implements Comparator<Tuple<Message, Connection>> { // --- MODIFIED Interface
																			// ---
		@Override
		public int compare(Tuple<Message, Connection> tuple1,
				Tuple<Message, Connection> tuple2) {
			// Note: othProphetRouterForwarding is guaranteed non-null when this comparator
			// is used (GRTRMax/GRTRSort)

			// Mendapatkan router peer untuk kedua tuple
			ProphetRouterForwardingMod otherRouter1 = (ProphetRouterForwardingMod) tuple1.getValue()
					.getOtherNode(getHost()).getRouter(); // --- MODIFIED (Cast here) ---
			ProphetRouterForwardingMod otherRouter2 = (ProphetRouterForwardingMod) tuple2.getValue()
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
		forwardedCounts.remove(id);
		forwardProgresses.remove(id);

		super.deleteMessage(id, drop);
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
			return;
		}
		String msgId = transferredMsg.getId();

		// --- Update MOFO (Forward Count) ---
		// Ambil hitungan saat ini, jika tidak ada default 0, tambahkan 1
		int currentCount = forwardedCounts.getOrDefault(msgId, 0);
		forwardedCounts.put(msgId, currentCount + 1);

		// --- Update MOPR (Forwarding Progress) ---
		// FP = FPold + P(B,D)
		// B adalah receiver, D adalah tujuan pesan transferredMsg.getTo()
		// Kita perlu nilai P(B,D), yaitu prediktabilitas receiver (B) ke tujuan pesan
		// (D).
		// Ini membutuhkan router 'receiver' untuk menjadi ProphetRouterForwarding.
		// Jika bukan, P(B,D) dianggap 0 karena tidak ada prediktabilitas Prophet.
		double pBD = 0.0;
		if (receiver.getRouter() instanceof ProphetRouterForwardingMod) {
			ProphetRouterForwardingMod receiverRouter = (ProphetRouterForwardingMod) receiver.getRouter();
			pBD = receiverRouter.getPredFor(transferredMsg.getTo()); // Dapatkan P(B,D)
		}

		// Ambil FP saat ini, jika tidak ada default 0.0, tambahkan P(B,D)
		double currentFP = forwardProgresses.getOrDefault(msgId, 0.0);
		forwardProgresses.put(msgId, currentFP + pBD);
	}

	@Override
	public Message messageTransferred(String id, DTNHost from) {
		// Ini adalah metode dari MessageRouter, dipanggil di penerima setelah transfer.
		// Logika pembaruan state MOFO/MOPR seharusnya ada di sisi pengirim.
		// Jadi, kita hanya panggil superclass method di sini.
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
			if (!(excludeMsgBeingSent && isSending(m.getId()))) {
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
		Comparator<Message> secondaryTieBreaker = this::compareByQueueMode;

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
				// Jadi, tidak perlu reversed() pada Comparator utama.
				dropComparator = Comparator.comparingInt((Message m) -> forwardedCounts.getOrDefault(m.getId(), 0))
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
				dropComparator = Comparator.comparingDouble((Message m) -> getPredFor(m.getTo()))
						.thenComparing(secondaryTieBreaker) // Tie-breaker
						.reversed(); // Membalik urutan: yang terkecil jadi terbesar
				break;

			case MOPR:
				// Kebijakan: Drop pesan dengan forward progress (FP) tertinggi
				// Prioritas Drop TERTINGGI = FP TERBESAR
				// Comparator.comparingDouble(FP) defaultnya: kecil < besar (ascending)
				// Kita ingin yang TERBESAR FP-nya menjadi yang TERBESAR untuk Collections.max
				// Jadi, tidak perlu reversed() pada Comparator utama.
				// Penggunaan comparingDouble secara otomatis menangani perbandingan double
				// secara robust.
				dropComparator = Comparator
						.comparingDouble((Message m) -> forwardProgresses.getOrDefault(m.getId(), 0.0))
						.thenComparing(secondaryTieBreaker); // Tie-breaker. FP tertinggi akan dianggap "terbesar"
														// oleh comparator ini.
				break;

			default:
				throw new SimError("Unknown queueing policy " + queueingPolicyEnum);
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

		return messageToDrop;
	}
	// --- END: Implementasi Queueing Policy (Drop Logic) --- //
}