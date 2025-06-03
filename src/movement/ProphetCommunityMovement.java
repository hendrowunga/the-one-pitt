/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package movement;
/*
 * © 2025 hendrowunga, University of Sanata Dharma
 * Created on 5/18/25
 */
import core.Coord;
import core.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.HashMap; // Added import
import java.util.Map; // Added import

/**
 * Community-based movement model tailored for the Prophet paper simulation.
 * Nodes have a home community and probabilistic movement towards
 * their home, a gathering place, or other areas, based on their
 * current location.
 *
 * Areas are numbered 1-11 for Communities C1-C11 and 12 for Gathering Place G.
 * Home area is assigned based on the node's index within the 'mobile_nodes'
 * group
 * or a specific mapping, as described in the paper.
 * This model is ONLY for the mobile nodes group.
 */
public class ProphetCommunityMovement extends MovementModel {

    /** Area number for the Gathering Place (G) */
    public static final int GATHERING_AREA = 12;
    private static final int NUM_COMMUNITIES = 11; // Total communities C1-C11
    private static final int TOTAL_AREAS = NUM_COMMUNITIES + 1; // Total areas C1-C11 + G
    private static final int NODES_PER_COMMUNITY = 10; // As per paper

    // Maps node's internal ID within the mobile group to its home area number
    // (1-11)
    // This map should be static or initialized once for all instances.
    // Assumes mobile node IDs are 0 to 109.
    private static Map<Integer, Integer> nodeIdToHomeArea = new HashMap<>();
    private static boolean homeAreaMapInitialized = false;

    private Coord lastWaypoint; // The node's location after completing the previous path, managed internally
    private int homeArea; // The node's home community (1-11). Assigned based on node ID in
                          // getInitialLocation.
    private int currentArea; // The area the node is currently considered to be in (1-12). Based on
                             // lastWaypoint.
    // private DTNHost myHost; // Optional: if setHost method exists and is called
    // by simulator

    /**
     * Constructor for the movement model.
     * Reads settings. Home area assignment and initial location state happen in
     * getInitialLocation.
     *
     * @param settings The settings object containing parameters
     */
    public ProphetCommunityMovement(Settings settings) {
        super(settings);

        // Initialize the static map the first time any instance is created
        if (!homeAreaMapInitialized) {
            // Assumes mobile node IDs are 0 to 109
            for (int i = 0; i < NUM_COMMUNITIES; i++) { // For each community C1-C11
                int areaNumber = i + 1; // Area numbers 1 to 11
                for (int j = 0; j < NODES_PER_COMMUNITY; j++) { // 10 nodes per community
                    int nodeId = i * NODES_PER_COMMUNITY + j; // Node ID 0-9, 10-19, ..., 100-109
                    nodeIdToHomeArea.put(nodeId, areaNumber);
                }
            }
            homeAreaMapInitialized = true;
        }

        // Initialize state fields to placeholder/null.
        // They will be properly set in getInitialLocation() where the Host object is
        // assumed available.
        this.homeArea = -1;
        this.currentArea = -1;
        this.lastWaypoint = null;

        // No access to Host object here typically.
    }

    /**
     * Copy constructor for replication.
     *
     * @param rwp The model to replicate
     */
    protected ProphetCommunityMovement(ProphetCommunityMovement rwp) {
        super(rwp);
        this.homeArea = rwp.homeArea;
        this.currentArea = rwp.currentArea; // Copy current area state
        // Copy the lastWaypoint Coord object (handle null)
        this.lastWaypoint = (rwp.lastWaypoint != null) ? rwp.lastWaypoint.clone() : null;
        // this.myHost = rwp.myHost; // If host reference is copied
    }


    /**
     * Returns a possible initial placement for a host, ensuring it's
     * within its assigned home area initially. This method is called by the
     * framework
     * when a new host is created and the Host object is often available here.
     * It also sets the initial lastWaypoint and currentArea state for the model,
     * and ASSIGNS THE HOME AREA based on host ID.
     *
     * @param host The host this movement model is associated with (Assuming
     *             simulator passes this)
     * @return Random position on the map within the home area
     */
    @Override
    public Coord getInitialLocation() {
        assert rng != null : "MovementModel not initialized!";

        // Assign home area if not already assigned (fallback: random assignment)
        if (this.homeArea == -1) {
            // Assign randomly if not set (should be set in constructor if using host ID)
            this.homeArea = rng.nextInt(NUM_COMMUNITIES) + 1;
        }

        // Initial location is within the assigned home area
        Coord initialCoord = getCoordInArea(this.homeArea);

        // Set the initial internal state of the model
        this.lastWaypoint = initialCoord;
        this.currentArea = this.homeArea;

        return initialCoord;
    }

    /**
     * Generates a new path based on the probabilistic model.
     * Selects a destination area, then a coordinate within that area.
     * Uses the internally stored lastWaypoint as the path start.
     * The Host object might or might not be passed to this method depending on API.
     * Assuming it's not needed here if state (lastWaypoint, homeArea, currentArea,
     * rng) is sufficient.
     *
     * @return The generated Path
     */
    @Override
    public Path getPath() { // <-- Method signature likely remains without DTNHost parameter
        assert lastWaypoint != null
                : "lastWaypoint is not initialized! getInitialLocation() should be called first by framework.";
        assert rng != null : "MovementModel not initialized!"; // Should be initialized by super

        Path p;
        // Generate speed using the base class method (uses speed range from settings)
        double speed = generateSpeed(); // generateSpeed should be available from super

        // Add current location (our lastWaypoint) as the start of the path
        p = new Path(speed);
        p.addWaypoint(lastWaypoint.clone()); // Use lastWaypoint as the starting point for the new path

        // Determine the node's current area based on our last known location
        this.currentArea = getAreaFromCoord(lastWaypoint); // Requires getAreaFromCoord implementation

        // Choose the next destination area based on current area and home area
        int nextArea = chooseNextArea(this.currentArea, this.homeArea); // Pass current and home areas

        // Get a random coordinate within the chosen destination area
        Coord nextCoord = getCoordInArea(nextArea); // Requires getCoordInArea method

        // Add the destination coordinate to the path (path length 1)
        p.addWaypoint(nextCoord);

        // *** IMPORTANT STATE UPDATE ***
        // Update our internal state to reflect the *destination* of the path we just
        // generated.
        // When getPath is called again, this nextCoord will become the new start
        // (lastWaypoint).
        this.currentArea = nextArea; // Update currentArea to the destination area
        this.lastWaypoint = nextCoord; // Update lastWaypoint to the destination coordinate

        return p; // Return the path for the framework to use
    }

    /**
     * Creates a replicate of this movement model.
     *
     * @return A new ProphetCommunityMovement instance with the same state
     */
    @Override
    public ProphetCommunityMovement replicate() {
        return new ProphetCommunityMovement(this);
    }

    // chooseNextArea, chooseElsewhereArea, getCoordInArea, getAreaFromCoord
    // implementations remain the same as the previous revised draft.
    // Ensure they are included here.

    /**
     * Chooses the next area number (1-12) based on current area,
     * home area, and probabilities from Table I.
     *
     * @param currentArea The area the node is currently in (1-12)
     * @param homeArea    The node's home area (1-11)
     * @return The number of the chosen next area (1-12)
     */
    protected int chooseNextArea(int currentArea, int homeArea) {
        double probability = rng.nextDouble();

        double probToHome = 0.0;
        double probToGathering = 0.0;
        double probElsewhere = 0.0;

        if (currentArea == homeArea) {
            probToGathering = 0.8;
            probElsewhere = 0.2;
        } else if (currentArea == GATHERING_AREA) {
            probToHome = 0.9;
            probElsewhere = 0.1;
        } else { // Currently Elsewhere
            probToHome = 0.9;
            probElsewhere = 0.1;
        }

        if (probability <= probToHome) {
            return homeArea;
        } else if (probability <= probToHome + probToGathering) {
            return GATHERING_AREA;
        } else {
            return chooseElsewhereArea(currentArea, homeArea);
        }
    }

    /**
     * Chooses a random area number from 1 to TOTAL_AREAS, excluding the specified
     * areas.
     *
     * @param excludedArea1 The first area number to exclude
     * @param excludedArea2 The second area number to exclude
     * @return A random area number from the remaining valid options (1-TOTAL_AREAS)
     */
    protected int chooseElsewhereArea(int excludedArea1, int excludedArea2) {
        List<Integer> possibleAreas = new ArrayList<>();
        for (int i = 1; i <= TOTAL_AREAS; i++) {
            if (i != excludedArea1 && i != excludedArea2) {
                possibleAreas.add(i);
            }
        }
        if (possibleAreas.isEmpty()) {
            System.err.println("Warning: No 'Elsewhere' areas available! Ex1=" + excludedArea1 + ", Ex2="
                    + excludedArea2 + ". Falling back to home area.");
            return homeArea; // Fallback
        }
        Collections.shuffle(possibleAreas, rng);
        return possibleAreas.get(0);
    }

    /**
     * Returns a random coordinate within the specified area number's bounds.
     *
     * @param areaNumber The number of the area (1-12)
     * @return A random Coord within the specified area
     */
    protected Coord getCoordInArea(int areaNumber) {
        double x, y;
        double maxX = getMaxX(); // Should be available from super
        double maxY = getMaxY(); // Should be available from super

        double xQuarter = maxX / 4.0;
        double yThird = maxY / 3.0;

        switch (areaNumber) {
            case 1:
                x = rng.nextDouble() * xQuarter;
                y = rng.nextDouble() * yThird + 2.0 * yThird;
                return new Coord(x, y);
            case 2:
                x = rng.nextDouble() * xQuarter + xQuarter;
                y = rng.nextDouble() * yThird + 2.0 * yThird;
                return new Coord(x, y);
            case 3:
                x = rng.nextDouble() * xQuarter + 2.0 * xQuarter;
                y = rng.nextDouble() * yThird + 2.0 * yThird;
                return new Coord(x, y);
            case 4:
                x = rng.nextDouble() * xQuarter + 3.0 * xQuarter;
                y = rng.nextDouble() * yThird + 2.0 * yThird;
                return new Coord(x, y);

            case 5:
                x = rng.nextDouble() * xQuarter;
                y = rng.nextDouble() * yThird + yThird;
                return new Coord(x, y);
            case 6:
                x = rng.nextDouble() * xQuarter + xQuarter;
                y = rng.nextDouble() * yThird + yThird;
                return new Coord(x, y);
            case 7:
                x = rng.nextDouble() * xQuarter + 2.0 * xQuarter;
                y = rng.nextDouble() * yThird + yThird;
                return new Coord(x, y);
            case 8:
                x = rng.nextDouble() * xQuarter + 3.0 * xQuarter;
                y = rng.nextDouble() * yThird + yThird;
                return new Coord(x, y);

            case 9:
                x = rng.nextDouble() * xQuarter;
                y = rng.nextDouble() * yThird;
                return new Coord(x, y);
            case 10:
                x = rng.nextDouble() * xQuarter + xQuarter;
                y = rng.nextDouble() * yThird;
                return new Coord(x, y);
            case 11:
                x = rng.nextDouble() * xQuarter + 2.0 * xQuarter;
                y = rng.nextDouble() * yThird;
                return new Coord(x, y);
            case GATHERING_AREA:
                x = rng.nextDouble() * xQuarter + 3.0 * xQuarter;
                y = rng.nextDouble() * yThird;
                return new Coord(x, y);
            default:
                System.err.println("Warning: Requested coordinate for invalid area number: " + areaNumber
                        + ". Returning random coord.");
                return new Coord(rng.nextDouble() * getMaxX(), rng.nextDouble() * getMaxY());
        }
    }

    /**
     * Determines the area number (1-12) that a given coordinate falls into.
     *
     * @param c The coordinate
     * @return The area number (1-12) or -1 if outside defined areas
     */
    protected int getAreaFromCoord(Coord c) {
        double x = c.getX();
        double y = c.getY();
        double maxX = getMaxX(); // Should be available from super
        double maxY = getMaxY(); // Should be available from super

        double xQuarter = maxX / 4.0;
        double yThird = maxY / 3.0;

        int area = -1;

        if (y >= 2.0 * yThird && y <= maxY) {
            area = (x >= 0 && x < xQuarter) ? 1
                    : (x >= xQuarter && x < 2.0 * xQuarter) ? 2
                            : (x >= 2.0 * xQuarter && x < 3.0 * xQuarter) ? 3
                                    : (x >= 3.0 * xQuarter && x <= maxX) ? 4 : -1;
        } else if (y >= yThird && y < 2.0 * yThird) {
            area = (x >= 0 && x < xQuarter) ? 5
                    : (x >= xQuarter && x < 2.0 * xQuarter) ? 6
                            : (x >= 2.0 * xQuarter && x < 3.0 * xQuarter) ? 7
                                    : (x >= 3.0 * xQuarter && x <= maxX) ? 8 : -1;
        } else if (y >= 0 && y < yThird) {
            area = (x >= 0 && x < xQuarter) ? 9
                    : (x >= xQuarter && x < 2.0 * xQuarter) ? 10
                            : (x >= 2.0 * xQuarter && x < 3.0 * xQuarter) ? 11
                                    : (x >= 3.0 * xQuarter && x <= maxX) ? GATHERING_AREA : -1;
        }

        if (area == -1) {
            System.err.println("Warning: Coordinate " + c + " did not map to any defined area within " + maxX + "x"
                    + maxY + " bounds.");
        }
        return area;
    }

    // generateSpeed(), getMaxX(), getMaxY() are assumed to be available from
    // superclass MovementModel.
}
