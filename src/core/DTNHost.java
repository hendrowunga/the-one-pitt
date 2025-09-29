/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList; // Import untuk CopyOnWriteArrayList

import movement.MovementModel; // Import untuk MovementModel
import movement.Path;
import routing.MessageRouter;
import routing.RoutingInfo;
/**
 * A DTN capable host. Manages the state of a single node in the simulation,
 * including its location, movement, messages, and network interfaces.
 * Includes functionality to store and retrieve the node's movement trajectory history
 * and provides access to its movement model.
 *
 * @author original Aalto University, ComNet authors
 * @author hendrowunga (modifications for trajectory history and getMovement getter)
 */
public class DTNHost implements Comparable<DTNHost>, Iterable<Connection> {
	private static int nextAddress = 0; // Untuk memberikan alamat unik pada setiap node
	private int address; // Alamat unik node

	private Coord location; // Lokasi node saat ini (koordinat X, Y)
	private Coord destination; // Lokasi tujuan node saat ini (waypoint berikutnya)

	private MessageRouter router; // Router yang menangani pesan node
	private MovementModel movement; // Model pergerakan node (Perlu getter publik)
	private Path path; // Jalur pergerakan (path segment) yang sedang diikuti node
	private double speed; // Kecepatan node saat ini
	private double nextTimeToMove; // Waktu simulasi berikutnya node siap bergerak
	private String name; // Nama node (biasanya groupId + address)
	private List<MessageListener> msgListeners; // Listener untuk event pesan
	private List<MovementListener> movListeners; // Listener untuk event pergerakan
	private List<NetworkInterface> net; // Daftar antarmuka jaringan node
	private ModuleCommunicationBus comBus; // Bus komunikasi untuk modul node

	// --- List untuk menyimpan riwayat koordinat yang dilalui ---
	// Menggunakan CopyOnWriteArrayList untuk keamanan thread saat dibaca GUI
	/**
	 * Stores the sequence of coordinates representing the node's path history.
	 * Points are typically added upon reaching a new waypoint.
	 * @author hendrowunga
	 */
	private List<Coord> trajectoryHistory;


	static {
		DTNSim.registerForReset(DTNHost.class.getCanonicalName());
		reset();
	}

	/**
	 * Creates a new DTNHost.
	 *
	 * @param msgLs        List of message listeners.
	 * @param movLs        List of movement listeners.
	 * @param groupId      The group ID.
	 * @param interf       List of NetworkInterface prototypes.
	 * @param comBus       The module communication bus.
	 * @param mmProto      Prototype of the movement model.
	 * @param mRouterProto Prototype of the message router.
	 */
	public DTNHost(List<MessageListener> msgLs,
				   List<MovementListener> movLs,
				   String groupId, List<NetworkInterface> interf,
				   ModuleCommunicationBus comBus,
				   MovementModel mmProto, MessageRouter mRouterProto) {
		this.comBus = comBus;
		this.location = new Coord(0, 0);
		this.address = getNextAddress();
		this.name = groupId + address;
		this.net = new ArrayList<NetworkInterface>();

		// --- Inisialisasi riwayat trajektori ---
		this.trajectoryHistory = new CopyOnWriteArrayList<>();


		for (NetworkInterface i : interf) {
			NetworkInterface ni = i.replicate();
			ni.setHost(this);
			net.add(ni);
		}

		this.msgListeners = msgLs;
		this.movListeners = movLs;

		this.movement = mmProto.replicate(); // Replikasi model pergerakan
		this.movement.setComBus(comBus);
		setRouter(mRouterProto.replicate()); // Atur router

		this.location = movement.getInitialLocation(); // Dapatkan lokasi awal
		// --- Tambahkan lokasi awal ke riwayat trajektori ---
		this.trajectoryHistory.add(this.location.clone()); // Tambahkan salinan lokasinya


		this.nextTimeToMove = movement.nextPathAvailable(); // Tentukan waktu siap bergerak pertama
		this.path = null; // Path awal null

		if (movLs != null) {
			for (MovementListener l : movLs) {
				l.initialLocation(this, this.location);
			}
		}
	}

	/**
	 * Returns a new network interface address.
	 */
	private synchronized static int getNextAddress() {
		return nextAddress++;
	}

	/**
	 * Resets static fields.
	 */
	public static void reset() {
		nextAddress = 0;
	}

	/**
	 * Returns true if this node is currently active.
	 */
	public boolean isActive() {
		return this.movement.isActive();
	}

	/**
	 * Returns the MovementModel of this host.
	 * This method is required by NodeGraphic to check the type of movement model
	 * for conditional visualization and call shouldDrawTrajectoryHistory().
	 * @author hendrowunga
	 * @return The MovementModel instance used by this host.
	 */
	public MovementModel getMovement() {
		return this.movement;
	}


	/**
	 * Sets the message router.
	 */
	private void setRouter(MessageRouter router) {
		router.initialize(this, msgListeners);
		this.router = router;
	}

	/**
	 * Returns the router.
	 */
	public MessageRouter getRouter() {
		return this.router;
	}

	/**
	 * Returns the network address.
	 */
	public int getAddress() {
		return this.address;
	}

	/**
	 * Returns the ModuleCommunicationBus.
	 */
	public ModuleCommunicationBus getComBus() {
		return this.comBus;
	}

	/**
	 * Informs router about connection up.
	 */
	public void connectionUp(Connection con) {
		this.router.changedConnection(con);
	}

	/**
	 * Informs router about connection down.
	 */
	public void connectionDown(Connection con) {
		this.router.changedConnection(con);
	}

	/**
	 * Returns a copy of active connections.
	 */
	public List<Connection> getConnections() {
		List<Connection> lc = new ArrayList<Connection>();
		for (NetworkInterface i : net) {
			lc.addAll(i.getConnections());
		}
		return lc;
	}

	/**
	 * Returns the current location.
	 */
	public Coord getLocation() {
		return this.location;
	}

	/**
	 * Returns the current path segment.
	 */
	public Path getPath() {
		return this.path;
	}

	/**
	 * Returns the complete trajectory history of the node.
	 * Returns a list of coordinates representing the path the node has taken
	 * since the start of the simulation, specifically logging initial location
	 * and each waypoint achieved.
	 * @author hendrowunga
	 * @return A List of Coord objects representing the trajectory history.
	 *         This is a thread-safe list for concurrent reading.
	 */
	public List<Coord> getTrajectoryHistory() {
		return this.trajectoryHistory;
	}


	/**
	 * Sets the Node's location. Primarily used for initial placement.
	 */
	public void setLocation(Coord location) {
		this.location.setLocation(location);
		// Note: Not adding to history here. History is added when reaching waypoints in move().
	}

	/**
	 * Sets the Node's name.
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Returns collection of messages.
	 */
	public Collection<Message> getMessageCollection() {
		return this.router.getMessageCollection();
	}

	/**
	 * Returns number of messages carried.
	 */
	public int getNrofMessages() {
		return this.router.getNrofMessages();
	}

	/**
	 * Returns buffer occupancy percentage.
	 */
	public double getBufferOccupancy() {
		double bSize = router.getBufferSize();
		double freeBuffer = router.getFreeBufferSize();
		if (bSize <= 0) return 0;
		return 100 * ((bSize - freeBuffer) / bSize);
	}

	/**
	 * Returns routing information.
	 */
	public RoutingInfo getRoutingInfo() {
		return this.router.getRoutingInfo();
	}

	/**
	 * Returns list of interfaces.
	 */
	public List<NetworkInterface> getInterfaces() {
		return net;
	}

	/**
	 * Finds interface by index (1-based).
	 */
	protected NetworkInterface getInterface(int interfaceNo) {
		NetworkInterface ni = null;
		try {
			ni = net.get(interfaceNo - 1);
		} catch (IndexOutOfBoundsException ex) {
			throw new SimError("No such interface with index " + interfaceNo + " for host " + this.name, ex);
		}
		return ni;
	}

	/**
	 * Finds interface by type string.
	 */
	protected NetworkInterface getInterface(String interfacetype) {
		for (NetworkInterface ni : net) {
			if (ni.getInterfaceType().equals(interfacetype)) {
				return ni;
			}
		}
		return null;
	}

	/**
	 * Forces connection/disconnection.
	 */
	public void forceConnection(DTNHost anotherHost, String interfaceId,
								boolean up) {
		NetworkInterface ni;
		NetworkInterface no;

		if (interfaceId != null) {
			ni = getInterface(interfaceId);
			no = anotherHost.getInterface(interfaceId);

			if (ni == null) throw new SimError("Host " + this.name + " tried to use a nonexisting interfacetype " + interfaceId);
			if (no == null) throw new SimError("Host " + anotherHost.name + " tried to use a nonexisting interfacetype " + interfaceId);
		} else {
			ni = getInterface(1); // getInterface is 1-based
			no = anotherHost.getInterface(1);

			if (!ni.getInterfaceType().equals(no.getInterfaceType()))
				throw new SimError("Interface types do not match (" + ni.getInterfaceType() + " != " + no.getInterfaceType() + ") for hosts " + this.name + " and " + anotherHost.name + ". Please specify interface type explicitly.");
		}

		if (up) {
			ni.createConnection(no);
		} else {
			ni.destroyConnection(anotherHost);
		}
	}

	/**
	 * FOR TESTS ONLY - DO NOT USE! Deprecated.
	 */
	@Deprecated
	public void connect(DTNHost h) {
		System.err.println(
				"WARNING: using deprecated DTNHost.connect(DTNHost)" +
						"\n Use DTNHost.forceConnection(DTNHost,null,true) instead");
		forceConnection(h, null, true);
	}

	/**
	 * Updates node's network layer and router.
	 *
	 * @param simulateConnections Update network layer too.
	 */
	public void update(boolean simulateConnections) {
		if (!isActive()) {
			return;
		}

		if (simulateConnections) {
			for (NetworkInterface i : net) {
				i.update();
			}
		}
		this.router.update();
	}

	/**
	 * Moves the node according to its path and speed.
	 * Also handles waiting periods between path segments.
	 * Trajectory history is added when snapping to a waypoint.
	 *
	 * @param timeIncrement The duration of the current simulation step.
	 */
	public void move(double timeIncrement) {
		double possibleMovement;
		double distance;
		double dx, dy;

		if (!isActive() || SimClock.getTime() < this.nextTimeToMove) {
			return;
		}
		if (this.destination == null) {
			if (!setNextWaypoint()) {
				return; // Node waits if no path available
			}
		}

		possibleMovement = timeIncrement * speed;
		distance = this.location.distance(this.destination);

		// Move loop: handle snapping to waypoints if possibleMovement is large enough
		while (possibleMovement >= distance) {
			this.location.setLocation(this.destination); // Snap to destination
			// --- Add the reached waypoint to history ---
			// This logs points where the node changes direction/segment.
			this.trajectoryHistory.add(this.location.clone()); // @author hendrowunga

			possibleMovement -= distance; // Reduce remaining movement capacity

			// Get next waypoint. If none, node stops for now.
			if (!setNextWaypoint()) {
				return;
			}
			// If new waypoint found, recalculate distance to new destination
			distance = this.location.distance(this.destination);
		}

		// If loop finished, node does not reach the next waypoint in this time step
		// Move partially towards the destination
		dx = (possibleMovement / distance) * (this.destination.getX() -
				this.location.getX());
		dy = (possibleMovement / distance) * (this.destination.getY() -
				this.location.getY());
		this.location.translate(dx, dy);

		// Note: Adding location after every small translation step here (optional: this.trajectoryHistory.add(this.location.clone());)
		// would create a denser history but significantly increase memory/CPU usage
		// for GUI drawing on long or large simulations. Logging only waypoints is standard.
		// The image (b) seems to connect points from the end of "flights" (waypoints).
	}

	/**
	 * Sets the next destination and speed based on the current path.
	 * If the current path is finished, it requests a new path from the movement model.
	 *
	 * @return True if a next waypoint was successfully set and node is ready to continue moving,
	 *         false if no more waypoints are available (current path finished and movement model
	 *         did not provide a new one) and node should transition to a waiting state.
	 */
	private boolean setNextWaypoint() {
		if (path == null) {
			// Request a new path from the movement model
			path = movement.getPath();
		}

		// Check if a path is available and has a next waypoint
		if (path == null || !path.hasNext()) {
			// No path or no more waypoints
			this.nextTimeToMove = movement.nextPathAvailable(); // Ask model when node is ready to move again
			this.path = null; // Clear current path
			this.destination = null; // Clear current destination
			return false; // Indicate node should wait
		}

		// A next waypoint is available
		this.destination = path.getNextWaypoint(); // Get destination coordinate
		this.speed = path.getSpeed(); // Get speed for this segment

		// Notify movement listeners about the new destination
		if (this.movListeners != null) {
			for (MovementListener l : this.movListeners) {
				l.newDestination(this, this.destination, this.speed);
			}
		}

		return true; // Indicate a next waypoint was set
	}

	/**
	 * Sends a message from this host to another host by passing it to the router.
	 *
	 * @param id Identifier of the message.
	 * @param to The destination host.
	 */
	public void sendMessage(String id, DTNHost to) {
		this.router.sendMessage(id, to);
	}

	/**
	 * Called by another host when it starts sending a message to this host.
	 * Passes the message to the router for processing (e.g., storing).
	 *
	 * @param m    The message being received.
	 * @param from The host sending the message.
	 * @return The result code from the router's receiveMessage method.
	 */
	public int receiveMessage(Message m, DTNHost from) {
		int retVal = this.router.receiveMessage(m, from);

		if (retVal == MessageRouter.RCV_OK) {
			m.addNodeOnPath(this); // Add this node to the message's path
		}

		return retVal;
	}

	/**
	 * Requests the router to find deliverable messages and start transferring them
	 * through the given connection.
	 *
	 * @param con The connection through which to send messages.
	 * @return True if the router started a transfer, false otherwise.
	 */
	public boolean requestDeliverableMessages(Connection con) {
		return this.router.requestDeliverableMessages(con);
	}

	/**
	 * Informs the host's router that a message was successfully transferred *from*
	 * this host *to* another host.
	 *
	 * @param id   Identifier of the message transferred.
	 * @param to   The host the message was transferred to.
	 */
	public void messageTransferred(String id, DTNHost to) {
		this.router.messageTransferred(id, to);
	}


	/**
	 * Informs the host's router that a message transfer was aborted.
	 *
	 * @param id             Identifier of the message.
	 * @param from           The host the message was being transferred from.
	 * @param bytesRemaining Nrof bytes remaining before completion.
	 */
	public void messageAborted(String id, DTNHost from, int bytesRemaining) {
		this.router.messageAborted(id, from, bytesRemaining);
	}

	/**
	 * Creates a new message originating from this host.
	 *
	 * @param m The message object to create.
	 */
	public void createNewMessage(Message m) {
		this.router.createNewMessage(m);
	}

	/**
	 * Deletes a message from this host's router.
	 *
	 * @param id   Identifier of the message to delete.
	 * @param drop True if dropped, false if delivered.
	 */
	public void deleteMessage(String id, boolean drop) {
		this.router.deleteMessage(id, drop);
	}

	/**
	 * Returns a string presentation of the host (its name).
	 *
	 * @return Host's name.
	 */
	@Override
	public String toString() {
		return name;
	}

	/**
	 * Checks equality by object reference.
	 */
	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (!(obj instanceof DTNHost)) return false;
		return this == (DTNHost)obj; // Comparison by reference is the definition of equality for DTNHost
	}

	/**
	 * Returns hash code based on address.
	 */
	@Override
	public int hashCode() {
		return Integer.valueOf(this.address).hashCode();
	}


	/**
	 * Compares two DTNHosts by their addresses.
	 */
	@Override
	public int compareTo(DTNHost h) {
		return this.getAddress() - h.getAddress();
	}

	/**
	 * Returns total number of active connections.
	 */
	public int getConnectionCount() {
		int sum = 0;
		for (NetworkInterface i : net) {
			sum += i.connectionCount();
		}
		return sum;
	}

	/**
	 * Returns an iterator over active connections.
	 */
	@Override
	public Iterator<Connection> iterator() {
		// ConnectionIterator must be implemented elsewhere, and it should safely
		// iterate over connections (e.g., by taking a snapshot of the connection list).
		return new ConnectionIterator(this); // Assumes ConnectionIterator exists
	}


    // --- METODE BARU YANG DITAMBAHKAN ---
    /**
     * Mengambil nilai initialEnergy dari router yang terpasang pada host ini.
     * Ini adalah cara yang aman untuk mengakses properti router dari luar.
     * @return Nilai initialEnergy yang dikonfigurasi.
     */
    public int getInitialEnergy() {
        if (this.router instanceof routing.carl_dtn.ContextAwareRLRouter) {
            return ((routing.carl_dtn.ContextAwareRLRouter) this.router).initialEnergy;
        }
        // Kembalikan nilai default jika router bukan tipe yang diharapkan
        // atau jika energi tidak didefinisikan.
        return 0;
    }

}