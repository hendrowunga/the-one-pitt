/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package gui.playfield;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.List;

import core.Connection;
import core.Coord;
import core.DTNHost;
import core.NetworkInterface;
import movement.MovementModel; // Pastikan MovementModel diimpor
import movement.Path;

/**
 * Visualization of a DTN Node. Configurable to show various elements like location,
 * ranges, connections, current path, and trajectory history.
 * Trajectory history is drawn conditionally based on a global GUI flag
 * AND whether the node's movement model indicates it should draw history.
 * Each visual component (body, name, etc.) is controlled by its own static flag.
 *
 * @author original Aalto University, ComNet authors
 * @author hendrowunga (modifications for conditional history drawing using MovementModel method and separate visual flags)
 */
public class NodeGraphic extends PlayFieldGraphic {
	// --- Flags untuk mengontrol visualisasi (diatur dari SimMenuBar) ---
	// Default bisa disesuaikan jika diinginkan. Untuk meniru Gambar (b) secara default,
	// kita bisa set default menjadi false untuk elemen selain history.
	/** Global flag to draw radio coverage circles. */ private static boolean drawCoverage = true;
	/** Global flag to draw node name strings. */ private static boolean drawNodeName = true;
	/** Global flag to draw connection lines. */ private static boolean drawConnections = true;
	/** Global flag to draw the current path segment (yellow/orange line). */ private static boolean drawPath = true;
	/** Global flag to draw the node's body (dot/rectangle). */ private static boolean drawNodeBody = true;
	/** Global flag to draw message bars. */ private static boolean drawMessages = true;

	// Bendera global untuk history. Ini adalah ENABLER global.
	/** Global flag to enable drawing of the full trajectory history. */
	private static boolean drawTrajectoryHistory = true;


	// --- Warna untuk elemen visualisasi ---
	private static Color rangeColor = Color.GREEN;
	private static Color conColor = Color.BLACK;
	private static Color hostColor = Color.BLUE; // Warna default titik node
	private static Color hostNameColor = Color.BLUE;
	private static Color msgColor1 = Color.BLUE; // Warna bar pesan
	private static Color msgColor2 = Color.GREEN; // Warna bar pesan
	private static Color msgColor3 = Color.RED; // Warna bar pesan
	// Warna untuk menggambar paths dan history
	private static final Color CURRENT_PATH_COLOR = Color.ORANGE;
	private static final Color HISTORY_COLOR = Color.BLACK; // Warna BLACK untuk meniru gambar (b)


	private DTNHost node;

	public NodeGraphic(DTNHost node) {
		this.node = node;
	}

	@Override
	public void draw(Graphics2D g2) {
		// --- Logika Drawing Utama ---

		// Gambar riwayat trajektori HANYA jika flag global history aktif
		// DAN model pergerakan node MENGATAKAN ia harus menggambar history.
		// Ini akan digambar di lapisan paling bawah.
		if (drawTrajectoryHistory && node.getMovement().shouldDrawTrajectoryHistory()) {
			drawTrajectoryHistory(g2);
		}

		// Gambar elemen standar node lainnya HANYA jika flag visualisasi global masing-masing aktif.
		// Ini digambar di lapisan di atas history.
		if (drawNodeBody) drawNodeBody(g2); // Gambar titik node itu sendiri

		// Pengecekan node.isActive() ditambahkan di dalam method gambar area jangkauan, koneksi, dan nama
		if (drawCoverage) drawCoverageArea(g2); // Gambar jangkauan
		if (drawConnections) drawConnectionsLines(g2); // Gambar koneksi

		if (drawNodeName) drawNodeLabel(g2); // Gambar nama node

		if (drawPath) drawPath(g2); // Gambar path segment saat ini

		if (drawMessages) drawMessagesBars(g2); // Gambar bar pesan (jika flag aktif)
		// --- END Logika Drawing Utama ---
	}

	/**
	 * Visualize the node's body (dot/rectangle).
	 * Controlled by drawNodeBody flag (checked in draw()).
	 * @param g2 The graphic context to draw to.
	 */
	private void drawNodeBody(Graphics2D g2) {
		Coord loc = node.getLocation();
		g2.setColor(hostColor); // Warna default titik node
		// Menggambar kotak kecil di lokasi node
		g2.drawRect(scale(loc.getX() - 1), scale(loc.getY() - 1), scale(2), scale(2));
		// Jika ingin persegi solid: g2.fillRect(scale(loc.getX() - 1), scale(loc.getY() - 1), scale(2), scale(2));
	}

	/**
	 * Visualize node's radio coverage circles.
	 * Controlled by drawCoverage flag (checked in draw()).
	 * Also checks if node is active.
	 * @param g2 The graphic context to draw to.
	 */
	private void drawCoverageArea(Graphics2D g2) {
		if (node.isActive()) { // Hanya gambar jangkauan jika node aktif
			Coord loc = node.getLocation();
			ArrayList<NetworkInterface> interfaces = new ArrayList<NetworkInterface>();
			interfaces.addAll(node.getInterfaces());
			for (NetworkInterface ni : interfaces) {
				double range = ni.getTransmitRange();
				Ellipse2D.Double coverage;

				coverage = new Ellipse2D.Double(scale(loc.getX()-range),
						scale(loc.getY()-range), scale(range * 2), scale(range * 2));

				g2.setColor(rangeColor); // Warna jangkauan
				g2.draw(coverage);
			}
		}
	}

	/**
	 * Visualize node's connections to other nodes.
	 * Controlled by drawConnections flag (checked in draw()).
	 * @param g2 The graphic context to draw to.
	 */
	private void drawConnectionsLines(Graphics2D g2) {
		g2.setColor(conColor); // Warna koneksi
		Coord c1 = node.getLocation();

		// Get connections safely (assuming DTNHost.getConnections returns a copy or thread-safe list)
		ArrayList<Connection> connectionsToDraw = new ArrayList<>();
		connectionsToDraw.addAll(node.getConnections());

		for (Connection c : connectionsToDraw) {
			if (c != null) {
				// Check if the connection involves this node (using fromNode/toNode)
				if (c.fromNode == node || c.toNode == node) {
					Coord c2 = c.getOtherNode(node).getLocation();
					g2.drawLine(scale(c1.getX()), scale(c1.getY()),
							scale(c2.getX()), scale(c2.getY()));
				}
			}
		}
	}

	/**
	 * Visualize node's name/address string.
	 * Controlled by drawNodeName flag (checked in draw()).
	 * @param g2 The graphic context to draw to.
	 */
	private void drawNodeLabel(Graphics2D g2) {
		Coord loc = node.getLocation();
		g2.setColor(hostNameColor); // Warna nama node
		g2.drawString(node.toString(), scale(loc.getX()), scale(loc.getY()));
	}


	/**
	 * Visualize the current path segment of the node.
	 * Controlled by drawPath flag (checked in draw()).
	 * @param g2 The graphic context to draw to.
	 */
	private void drawPath(Graphics2D g2) {
		// Flag drawPath is checked in the draw() method before calling this.

		Path currentPath = node.getPath(); // Get the node's current path segment
		if (currentPath == null) {
			return;
		}

		List<Coord> coords = currentPath.getCoords();
		if (coords == null || coords.isEmpty()) {
			return;
		}

		g2.setColor(CURRENT_PATH_COLOR); // Set the color for the current path segment

		// Draw line from current location to the first waypoint, then between subsequent waypoints
		Coord currentLocation = node.getLocation();
		Coord prev = currentLocation;

		Coord firstWaypoint = coords.get(0);
		g2.drawLine(scale(prev.getX()), scale(prev.getY()),
				scale(firstWaypoint.getX()), scale(firstWaypoint.getY()));
		prev = firstWaypoint;

		for (int i = 1, n = coords.size(); i < n; i++) {
			Coord next = coords.get(i);
			g2.drawLine(scale(prev.getX()), scale(prev.getY()),
					scale(next.getX()), scale(next.getY()));
			prev = next;
		}
	}

	/**
	 * Visualize the entire trajectory history of the node.
	 * This method is called if drawTrajectoryHistory flag is true AND the node's
	 * movement model indicates it should draw history (via shouldDrawTrajectoryHistory()).
	 * @param g2 The graphic context to draw to.
	 */
	private void drawTrajectoryHistory(Graphics2D g2) {
		// Global flag and model check done in draw() before calling this.

		List<Coord> history = node.getTrajectoryHistory(); // Get the trajectory history
		if (history == null || history.size() < 2) { // Need at least 2 points to draw a line
			return;
		}

		g2.setColor(HISTORY_COLOR); // Set the color for the history line (BLACK)

		Coord prev = history.get(0); // Start from the first recorded point

		for (int i = 1, n = history.size(); i < n; i++) {
			Coord next = history.get(i);
			if (prev != null && next != null) { // Safety check
				g2.drawLine(scale(prev.getX()), scale(prev.getY()),
						scale(next.getX()), scale(next.getY()));
			}
			prev = next;
		}
	}


	// --- Setters for the global visualization flags ---
	/** Sets global flag for drawing radio coverage. */ public static void setDrawCoverage(boolean draw) { drawCoverage = draw; }
	/** Sets global flag for drawing node name strings. */ public static void setDrawNodeName(boolean draw) { drawNodeName = draw; }
	/** Sets global flag for drawing connections. */ public static void setDrawConnections(boolean draw) { drawConnections = draw; }
	/** Sets global flag for drawing the current path segment. */ public static void setDrawPath(boolean draw) { drawPath = draw; }
	/** Sets global flag for drawing the node's body (dot). */ public static void setDrawNodeBody(boolean draw) { drawNodeBody = draw; }
	/** Sets global flag for drawing trajectory history. */ public static void setDrawTrajectoryHistory(boolean draw) { drawTrajectoryHistory = draw; }
	/** Sets global flag for drawing message bars. */ public static void setDrawMessages(boolean draw) { drawMessages = draw; }


	/** Visualizes messages as bar stacks. */
	private void drawMessagesBars(Graphics2D g2) { // Nama method lebih deskriptif
		int nrofMessages = node.getNrofMessages();
		if (nrofMessages > 0) { // Hanya gambar jika ada pesan
			Coord loc = node.getLocation();
			drawBar(g2, loc, nrofMessages % 10, 1); // Draw count 0-9
			drawBar(g2, loc, nrofMessages / 10, 2); // Draw count 10, 20, ...
		}
	}

	/** Helper to draw a message bar stack. */
	private void drawBar(Graphics2D g2, Coord loc, int nrof, int col) {
		final int BAR_HEIGHT = 5;
		final int BAR_WIDTH = 5;
		final int BAR_DISPLACEMENT = 2;

		for (int i = 1; i <= nrof; i++) {
			if (i % 2 == 0) { g2.setColor(msgColor1); }
			else { if (col > 1) { g2.setColor(msgColor3); } else { g2.setColor(msgColor2); } }
			g2.fillRect(scale(loc.getX() - BAR_DISPLACEMENT - (BAR_WIDTH * col)),
					scale(loc.getY() - BAR_DISPLACEMENT - i * BAR_HEIGHT),
					scale(BAR_WIDTH), scale(BAR_HEIGHT));
		}
	}
}