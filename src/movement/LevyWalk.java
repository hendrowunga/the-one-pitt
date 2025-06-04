/*
 * © 2025 hendrowunga, University of Sanata Dharma
 * Created on 5/30/25
 */
package movement;

import core.*;

public class LevyWalk extends MovementModel {

    /**
     * Shape parameter (alpha) for the Pareto-derived step length distribution.
     * Smaller alpha means more frequent long jumps. Typical range 0 < alpha <= 2.
     * Setting ID: {@value}. Default: {@link #DEFAULT_ALPHA}.
     */
    public static final String ALPHA_S = "alpha";
    /**
     * Minimum step length component. Added to the scaled Pareto sample.
     * Setting ID: {@value}. Default: {@link #DEFAULT_MIN_STEP}.
     */
    public static final String MIN_STEP_S = "minStep";
    /**
     * Scale factor component for the Pareto sample.
     * Setting ID: {@value}. Default: {@link #DEFAULT_SCALE_FACTOR}.
     */
    public static final String SCALE_FACTOR_S = "scaleFactor";

    public static final double DEFAULT_ALPHA = 1.5;
    public static final double DEFAULT_MIN_STEP = 0.1;
    public static final double DEFAULT_SCALE_FACTOR = 1.0;

    private double alpha;
    private double minStep;
    private double scaleFactor;

    private Coord currentPosition; // Stores the node's current location

    /**
     * Creates a new LevyWalk movement model based on a Settings object's settings.
     * Reads Levy Walk specific parameters (alpha, minStep, scaleFactor) from the
     * settings object provided by the simulator (scoped to the host group).
     *
     * @param settings The Settings object where the settings are read from (scoped to the host group).
     * @throws IllegalArgumentException if settings values are invalid.
     */
    public LevyWalk(Settings settings) {
        super(settings); // Read common settings (speed, waitTime, worldSize) from the group scope

        // Read specific Levy Walk settings from the provided Settings object
        this.alpha = settings.contains(ALPHA_S) ? settings.getDouble(ALPHA_S) : DEFAULT_ALPHA;
        this.minStep = settings.contains(MIN_STEP_S) ? settings.getDouble(MIN_STEP_S) : DEFAULT_MIN_STEP;
        this.scaleFactor = settings.contains(SCALE_FACTOR_S) ? settings.getDouble(SCALE_FACTOR_S) : DEFAULT_SCALE_FACTOR;

        // --- Validate Settings ---
        if (this.alpha <= 0) {
            throw new IllegalArgumentException(ALPHA_S + " must be > 0. Value was " + this.alpha);
        }
        if (this.minStep < 0) {
            throw new IllegalArgumentException(MIN_STEP_S + " must be >= 0. Value was " + this.minStep);
        }
        // Add validation for scaleFactor if needed (e.g., must be > 0)

        // currentPosition will be initialized by getInitialLocation() when first needed
        this.currentPosition = null;
    }

    /**
     * Copy constructor. Creates a new instance with the same settings
     * and state (currentPosition) as the prototype. Uses the shared static RNG.
     * @param proto The LevyWalk prototype to copy.
     */
    protected LevyWalk(LevyWalk proto) {
        super(proto); // Copy common settings
        // Copy specific Levy Walk parameters
        this.alpha = proto.alpha;
        this.minStep = proto.minStep;
        this.scaleFactor = proto.scaleFactor;

        // Copy current position if it exists
        if (proto.currentPosition != null) {
            this.currentPosition = proto.currentPosition.clone();
        } else {
            this.currentPosition = null;
        }
    }

    /**
     * Returns a random initial placement for a host within world boundaries.
     * This location is also set as the node's initial current position.
     * @return Initial random position.
     */
    @Override
    public Coord getInitialLocation() {
        assert rng != null : "MovementModel RNG not initialized!";

        double worldX = getMaxX(); // Inherited from superclass
        double worldY = getMaxY(); // Inherited from superclass

        // Generate random coordinates within the world boundaries
        double x = rng.nextDouble() * worldX;
        double y = rng.nextDouble() * worldY;

        this.currentPosition = new Coord(x, y);
        return this.currentPosition.clone(); // Return a copy
    }

    /**
     * Generates and returns the next path for the node (a single step/flight).
     * The step length is derived from a Pareto distribution (non-standard parameterization).
     * Direction is random. Boundary handling uses a 'stay put' strategy if the step
     * goes out of bounds.
     * @return A Path object representing the single step, starting from the position
     *         before the step and ending at the position after the step/boundary check.
     * @throws IllegalStateException if the MovementModel RNG is not initialized.
     */
    @Override
    public Path getPath() {
        assert rng != null : "MovementModel RNG not initialized!";

        // Ensure currentPosition is initialized
        if (this.currentPosition == null) {
            getInitialLocation(); // Fallback
        }

        // Store the position at the start of this step
        Coord startPosition = this.currentPosition.clone();

        // Create a Path object for this step. It will contain the start and end points.
        Path path = new Path(generateSpeed()); // Speed from superclass settings
        // Adding the start position as the first waypoint is optional but aligns with some path representations
        // path.addWaypoint(startPosition); // Uncomment if you want start point explicitly in path waypoints

        // --- Generate Step Length (Non-Standard Pareto Derivation) ---
        double u = 0.0;
        while (u == 0.0) { // Ensure u is not zero for Math.pow
            u = rng.nextDouble();
        }
        // Sample from Pareto Type I with scale parameter xm=1
        double paretoSample_xm1 = Math.pow(u, -1.0 / this.alpha);
        // Calculate the final step length using minStep and scaleFactor
        double stepLength = this.minStep + paretoSample_xm1 * this.scaleFactor;
        // --- End Step Length Generation ---


        // --- Generate Random Direction (Uniform [0, 2*PI)) ---
        double theta = rng.nextDouble() * 2.0 * Math.PI;
        // --- End Direction Generation ---

        // --- Calculate Potential New Location ---
        double dx = stepLength * Math.cos(theta);
        double dy = stepLength * Math.sin(theta);
        double potentialNewX = this.currentPosition.getX() + dx;
        double potentialNewY = this.currentPosition.getY() + dy;
        // --- End Calculate Potential New Location ---


        // --- Boundary Handling ('Stay Put' Strategy) ---
        double worldMaxX = getMaxX(); // World width
        double worldMaxY = getMaxY(); // World height

        if (potentialNewX >= 0 && potentialNewX <= worldMaxX && potentialNewY >= 0 && potentialNewY <= worldMaxY) {
            // If potential location is within bounds, update the node's current position
            this.currentPosition.setLocation(potentialNewX, potentialNewY);
        }
        // If potential location is out of bounds, this.currentPosition is NOT updated.
        // The node effectively stays at startPosition for this step.
        // --- End Boundary Handling ---

        // Add the final position (could be the new location or the old location if out of bounds)
        // as the destination waypoint for this path segment.
        path.addWaypoint(this.currentPosition.clone());

        return path; // Return the path (single step from startPosition to currentPosition)
    }

    /**
     * Returns the simulation time when the next path is available.
     * Returns 0, indicating no pause time between steps.
     * @return The current simulation time (ready immediately).
     */
    @Override
    public double nextPathAvailable() {
        return 0; // No pause time
    }

    /**
     * Creates a replicate of this movement model instance using the copy constructor.
     * @return A new LevyWalk instance with the same parameters and state.
     */
    @Override
    public LevyWalk replicate() {
        return new LevyWalk(this);
    }

    /**
     * Indicates whether this movement model is designed to have its full trajectory
     * history drawn on the GUI (if the global GUI option is enabled).
     * Levy Walk models are typically visualized with their full path.
     * @author hendrowunga
     * @return true.
     */
    @Override
    public boolean shouldDrawTrajectoryHistory() {
        return true; // This model wants its history drawn (if GUI allows)
    }


}