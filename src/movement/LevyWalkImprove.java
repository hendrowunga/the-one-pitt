package movement;
/*
 * @author hendrowunga, University of Sanata Dharma
 * @version 1.0
 * @since 5/30/25
 */

import core.*;

public class LevyWalkImprove extends MovementModel {

    private static final String ALPHA_S = "alpha";
    private static final String MIN_STEP_LENGTH_S = "min_step_length";
    private static final String MAX_STEP_LENGTH_S = "max_step_length";

    private static final String PAUSE_MODEL_S = "pause_model"; // Although we only implement PARETO here
    private static final String PAUSE_BETA_S = "pause_beta"; // Shape parameter for pause time Pareto
    private static final String MIN_PAUSE_TIME_S = "min_pause_time";
    private static final String MAX_PAUSE_TIME_S = "max_pause_time";
    private static final String PAUSE_MODEL_PARETO = "PARETO"; // Expected value for PAUSE_MODEL_S

    private double alpha; // Step length alpha
    private double minStepLength;
    private double maxStepLength;

    private double pauseBeta; // Pause time beta (alpha in pause context)
    private double minPauseTime;
    private double maxPauseTime;
    private String pauseModel; // Currently only supports PARETO

    private Coord lastWaypoint;
    private ParetoRNG stepLengthRNG;
    private ParetoRNG pauseTimeRNG; // New RNG for pause times

    /**
     * Creates a new movement model based on a Settings object's settings.
     * Reads step length and pause time parameters.
     * Initializes the ParetoRNGs.
     *
     * @param s The Settings object where the settings are read from
     * @throws SimError if settings are invalid.
     */
    public LevyWalkImprove(Settings s) {
        super(s); // Call superclass constructor to read common settings (speed, wait time, world size)

        try {
            this.alpha = s.getDouble(ALPHA_S);
             System.out.println(this.alpha); // Keep for debugging if needed
        } catch (SettingsError e) {
            System.err.println("Setting '" + ALPHA_S + "' not found, using default: 0.9");
            this.alpha = 0.9; // Default as in your last code
        }
        try {
            this.minStepLength = s.getDouble(MIN_STEP_LENGTH_S);
        } catch (SettingsError e) {
            System.err.println("Setting '" + MIN_STEP_LENGTH_S + "' not found, using default: 1.0");
            this.minStepLength = 1.0;
        }
        try {
            maxStepLength = s.getDouble(MAX_STEP_LENGTH_S);
        } catch (SettingsError e) {
            System.err.println("Setting '" + MAX_STEP_LENGTH_S + "' not found, using default: 1000.0");
            this.maxStepLength = 1000.0;
        }

        // Use settings.getSetting for PAUSE_MODEL_S as it's a String
        try {
            this.pauseModel = s.getSetting(PAUSE_MODEL_S);
            if (!this.pauseModel.equalsIgnoreCase(PAUSE_MODEL_PARETO)) {
                // If other models are needed, add more logic here.
                // For now, we only support PARETO for heavy-tailed pauses.
                throw new SettingsError("Unsupported pause_model '" + this.pauseModel + "'. Only '" + PAUSE_MODEL_PARETO + "' is supported for heavy-tailed pauses.");
            }
        } catch (SettingsError e) {
            // Default to PARETO if setting is missing
            System.err.println("Setting '" + PAUSE_MODEL_S + "' not found or invalid, defaulting to '" + PAUSE_MODEL_PARETO + "' for pause time.");
            this.pauseModel = PAUSE_MODEL_PARETO;
        }

        try {
            this.pauseBeta = s.getDouble(PAUSE_BETA_S); // Use 'beta' as the shape parameter name for pauses
        } catch (SettingsError e) {
            System.err.println("Setting '" + PAUSE_BETA_S + "' not found, using default: 2.8");
            this.pauseBeta = 2.8; // Default from your original settings
        }
        try {
            this.minPauseTime = s.getDouble(MIN_PAUSE_TIME_S);
        } catch (SettingsError e) {
            System.err.println("Setting '" + MIN_PAUSE_TIME_S + "' not found, using default: 0.1");
            this.minPauseTime = 0.1; // Default from your original settings
        }
        try {
            this.maxPauseTime = s.getDouble(MAX_PAUSE_TIME_S);
        } catch (SettingsError e) {
            System.err.println("Setting '" + MAX_PAUSE_TIME_S + "' not found, using default: 3600.0");
            this.maxPauseTime = 3600.0; // Default from your original settings
        }



        // RNG for step lengths (Truncated Pareto)
        this.stepLengthRNG = new ParetoRNG(rng, this.alpha, this.minStepLength, this.maxStepLength);

        // RNG for pause times (Truncated Pareto, using beta as alpha parameter)
        // Only create if pauseModel is PARETO
        if (this.pauseModel.equalsIgnoreCase(PAUSE_MODEL_PARETO)) {
            this.pauseTimeRNG = new ParetoRNG(rng, this.pauseBeta, this.minPauseTime, this.maxPauseTime);
        } else {
            // Fallback to uniform pause time if not PARETO? Or throw error earlier?
            // The validation above should catch unsupported models.
            this.pauseTimeRNG = null;
        }


        this.lastWaypoint = null; // Will be set by getInitialLocation()
    }

    /**
     * Copy constructor. Creates a new instance with the same settings
     * and state (lastWaypoint) as the prototype. Note: RNG state is NOT copied,
     * it uses the shared static RNG. New ParetoRNG instances are created using the shared RNG.
     *
     * @param tlw The TruncatedLevyWalkMobilityImprove prototype
     */
    protected LevyWalkImprove(LevyWalkImprove tlw) {
        super(tlw); // Call superclass copy constructor

        // Copy specific settings
        alpha = tlw.alpha;
        minStepLength = tlw.minStepLength;
        maxStepLength = tlw.maxStepLength;

        pauseModel = tlw.pauseModel;
        pauseBeta = tlw.pauseBeta;
        minPauseTime = tlw.minPauseTime;
        maxPauseTime = tlw.maxPauseTime;


        // Create new ParetoRNG instances using the shared static RNG
        if (rng == null) {
            throw new IllegalStateException("MovementModel RNG is not initialized!");
        }
        this.stepLengthRNG = new ParetoRNG(rng, alpha, minStepLength, maxStepLength);

        // Only create pause time RNG if using Pareto pause model
        if (this.pauseModel.equalsIgnoreCase(PAUSE_MODEL_PARETO)) {
            this.pauseTimeRNG = new ParetoRNG(rng, pauseBeta, minPauseTime, maxPauseTime);
        } else {
            this.pauseTimeRNG = null; // Indicates uniform/default pause time
        }


        this.lastWaypoint = (tlw.lastWaypoint != null) ? tlw.lastWaypoint.clone() : null;
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
     * Generates and returns the next path (one Levy "flight").
     * The path starts from the current location (lastWaypoint).
     * Uses uniform speed and 'rejection sampling until valid' boundary handling.
     * A new step length and direction are sampled if the potential new location
     * falls outside the world bounds.
     *
     * @return A Path object representing one flight segment.
     * @throws IllegalStateException if initial location is not set.
     */
    @Override
    public Path getPath() {
        if (this.lastWaypoint == null) {
            throw new IllegalStateException("Initial location not set! Call getInitialLocation first.");
        }
        // rng is from superclass
        // stepLengthRNG is initialized in constructor

        Coord oldLocation = this.lastWaypoint; // The current location of the node

        // Get world dimensions from superclass
        double maxX = getMaxX();
        double maxY = getMaxY();

        Coord newLocation = null; // Variable to store the valid final location for this step

        // Loop to find a valid new location within bounds using rejection sampling
        while (true) {
            double stepLength = this.stepLengthRNG.getDouble(); // Sample truncated Pareto step length
            double direction = rng.nextDouble() * 2 * Math.PI; // Sample random direction [0, 2*PI)

            // Calculate potential new coordinates
            double x = oldLocation.getX() + stepLength * Math.cos(direction);
            double y = oldLocation.getY() + stepLength * Math.sin(direction);

            Coord potentialNewLocation = new Coord(x, y); // Create potential new location object

            // Check boundary conditions: is the potential new location within [0, maxX] x [0, maxY]?
            // Note: The comparison should probably use <= for max bounds, not < (The original code used <=, let's keep that consistency)
            if (x >= 0 && x <= maxX && y >= 0 && y <= maxY) {
                // If the potential location is within bounds, this is our valid new location
                newLocation = potentialNewLocation;
                break; // Exit the while loop
            }

            // If the potential location is outside bounds, the loop continues.
            // A *new* stepLength and direction will be sampled from oldLocation
            // in the next iteration until a valid newLocation is found.
        }

        // Once a valid newLocation is found, create a path for this single step
        Path p = new Path(generateSpeed()); // Generate a speed using the inherited method (uniform)
        p.addWaypoint(newLocation); // Add the valid new location as the destination waypoint

        // Update the lastWaypoint to the new valid location
        this.lastWaypoint = newLocation;

        return p; // Return the path for the simulator to execute
    }

    /**
     * Returns a sim time when the next path is available.
     * This method overrides the superclass and generates pause time
     * using the Pareto distribution if configured, otherwise uses uniform
     * distribution based on min/max wait time settings.
     *
     * @return The sim time when node should ask the next time for a path
     */
    @Override
    public double nextPathAvailable() {
        double waitDuration;
        if (this.pauseTimeRNG != null) {
            // Use Pareto distributed pause time (Truncated Pareto)
            waitDuration = this.pauseTimeRNG.getDouble();
        } else {
            // If pauseModel was not PARETO, use uniform pause time from superclass settings (waitTime)
            waitDuration = super.generateWaitTime(); // Use the inherited method that reads minWaitTime/maxWaitTime
        }
        return SimClock.getTime() + waitDuration; // SimClock.getTime() from core
    }


    /**
     * Creates a replicate of the movement model using the copy constructor.
     *
     * @return A new movement model with the same settings as this model.
     */
    @Override

    public MovementModel replicate() {
        return new LevyWalkImprove(this);
    }


    /**
     * Returns the last known waypoint (current position) of the node.
     * @return The last waypoint or null if getInitialLocation hasn't been called.
     */
    public Coord getLastWaypoint() {
        return lastWaypoint;
    }
    /**
     * Override the default shouldDrawTrajectoryHistory to return true.
     * This tells NodeGraphic (and other components) that nodes using THIS model
     * ARE designed to potentially show trajectory history visualization.
     * @author hendrowunga
     * @return true
     */
    @Override
    public boolean shouldDrawTrajectoryHistory() {
        return true; // This model wants its history drawn (if GUI allows)
    }
}