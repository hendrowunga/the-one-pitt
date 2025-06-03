package movement;

import core.*;


/*
 * © 2025 hendrowunga, University of Sanata Dharma
 * Created on 5/30/25
 */
public class LevyWalk extends MovementModel {
    private static final String ALPHA_S = "alpha"; // Shape parameter for Pareto step length
    private static final String MIN_STEP_LENGTH_S = "min_step_length"; // Minimum step length (Pareto scale parameter)

    private double alpha; // Shape parameter for Pareto step length
    private double minStepLength; // Minimum step length (Pareto scale parameter)

    private Coord lastWaypoint; // Stores the node's current location

    /**
     * Creates a new movement model based on a Settings object's settings.
     * Reads Levy Walk specific parameters (alpha, min_step_length).
     *
     * @param s The Settings object where the settings are read from
     * @throws SimError if settings are invalid.
     */
    public LevyWalk(Settings s) {
        super(s); // Call superclass constructor to read common settings (speed, world size)

        try {
            this.alpha = s.getDouble(ALPHA_S);
        } catch (SettingsError e) {
            System.err.println("Setting '" + ALPHA_S + "' not found, using default: 1.5");
            this.alpha = 1.5; // Default alpha, common in Levy Walk literature
        }
        try {
            // This min_step_length acts as the 'xm' or scale parameter for Pareto Type I
            this.minStepLength = s.getDouble(MIN_STEP_LENGTH_S);
        } catch (SettingsError e) {
            System.err.println("Setting '" + MIN_STEP_LENGTH_S + "' not found, using default: 1.0");
            this.minStepLength = 1.0; // Default minimum step length
        }


        // --- Validate Levy Walk specific settings ---
        if (this.alpha <= 0) {
            throw new SimError("LevyWalk: " + ALPHA_S + " (" + this.alpha + ") must be positive.");
        }
        if (this.minStepLength <= 0) {
            throw new SimError(
                    "LevyWalk: " + MIN_STEP_LENGTH_S + " (" + this.minStepLength + ") must be positive.");
        }

        // Initial location is set by calling getInitialLocation() later by the simulator
        this.lastWaypoint = null; // Will be set by getInitialLocation()
    }

    /**
     * Copy constructor. Creates a new instance with the same settings
     * and state (lastWaypoint) as the prototype. Uses the shared static RNG.
     *
     * @param proto The LevyWalk prototype
     */
    protected LevyWalk(LevyWalk proto) {
        super(proto); // Call superclass copy constructor

        // Copy specific settings
        alpha = proto.alpha;
        minStepLength = proto.minStepLength;

        // Copy current location
        this.lastWaypoint = (proto.lastWaypoint != null) ? proto.lastWaypoint.clone() : null;
    }

    /**
     * Returns a possible (random) initial placement for a host.
     * Uses randomCoord() method (inherited from MovementModel) to get a random location.
     * Stores this as the starting point for subsequent paths.
     *
     * @return Random position on the map.
     */
    @Override
    public Coord getInitialLocation() {
        Coord c = randomCoord();
        this.lastWaypoint = c; // Store the initial location
        return c;
    }

    /**
     * Generates and returns the next path (one Levy "flight") based on Python logic.
     * The path starts from the current location (lastWaypoint).
     * Samples step length from Pareto Type I (xm=minStepLength, alpha=alpha).
     * Samples direction uniformly.
     * If the potential new location is out of bounds, the node stays put.
     *
     * @return A Path object representing one flight segment (or staying put).
     * @throws IllegalStateException if initial location is not set.
     */
    @Override
    public Path getPath() {
        if (this.lastWaypoint == null) {
            throw new IllegalStateException("Initial location not set! Call getInitialLocation first.");
        }

        Coord oldLocation = this.lastWaypoint; // The current location of the node

        // Get world dimensions from superclass
        double maxX = getMaxX();
        double maxY = getMaxY();

        // --- 1. Generate Step Length (Pareto Type I, xm = minStepLength, alpha) ---
        // Using inverse CDF for Pareto Type I: X = xm / (U ^ (1/alpha)), where U is uniform (0, 1]
        // rng.nextDouble() gives [0.0, 1.0). Using 1.0 - rng.nextDouble() gives (0.0, 1.0]
        double stepLength = this.minStepLength / Math.pow(1.0 - rng.nextDouble(), 1.0 / this.alpha);

        // --- 2. Generate Random Direction [0, 2*PI) ---
        double direction = rng.nextDouble() * 2.0 * Math.PI;

        // --- 3. Calculate Potential New Location ---
        double x = oldLocation.getX() + stepLength * Math.cos(direction);
        double y = oldLocation.getY() + stepLength * Math.sin(direction);

        Coord potentialNewLocation = new Coord(x, y);

        // --- 4. Boundary Handling (Python Style: Stay Put if Out of Bounds) ---
        Coord finalLocation;
        if (x >= 0 && x <= maxX && y >= 0 && y <= maxY) {
            // If potential location is within bounds, this is the final location
            finalLocation = potentialNewLocation;
        } else {
            // If potential location is out of bounds, the node stays at the old location
            finalLocation = oldLocation; // Note: this is the OLD location
            // System.out.println("Step out of bounds, staying put at: " + oldLocation); // Debugging
        }

        // Update the lastWaypoint to the final decided location (either new or old)
        this.lastWaypoint = finalLocation;

        // --- 5. Create Path ---
        // A path always goes from the starting point of the step to the final location
        // The speed is generated uniformly using the inherited method
        Path p = new Path(generateSpeed());
        p.addWaypoint(finalLocation); // Add the final location as the destination

        return p; // Return the path
    }

    /**
     * Returns a sim time when the next path is available.
     * Mimics Python behavior by having no explicit pause time.
     * The next path is available immediately after the current one finishes.
     *
     * @return The current simulation time (meaning immediately available)
     */
    @Override
    public double nextPathAvailable() {
        // In this Python-style implementation, there are no pauses between steps.
        // The node is ready for the next step as soon as the current one finishes.
        return SimClock.getTime();
    }

    /**
     * Creates a replicate of the movement model using the copy constructor.
     *
     * @return A new movement model with the same settings and state as this model.
     */
    @Override
    public MovementModel replicate() {
        return new LevyWalk(this);
    }

    /**
     * Returns the last known waypoint (current position) of the node.
     * @return The last waypoint or null if getInitialLocation hasn't been called.
     */
    public Coord getLastWaypoint() {
        return lastWaypoint;
    }
}