package movement;
/*
 * © 2025 hendrowunga, University of Sanata Dharma
 * Created on 5/21/25
 */

import core.*;

public class TruncatedLevyWalkMobility extends MovementModel {

    private static final String ALPHA_S = "alpha";
    private static final String MIN_STEP_LENGTH_S = "min_step_length";
    private static final String MAX_STEP_LENGTH_S = "max_step_length";

    private double alpha;
    private double minStepLength;
    private double maxStepLength;

    private Coord lastWaypoint;
    private ParetoRNG stepLengthRNG;

    /**
     * Creates a new movement model based on a Settings object's settings.
     *
     * @param s The Settings object where the settings are read from
     */
    public TruncatedLevyWalkMobility(Settings s) {
        super(s);

        try {
            this.alpha = s.getDouble(ALPHA_S);
        } catch (SettingsError e) {
            this.alpha = 1.5;
        }
        try {
            this.minStepLength = s.getDouble(MIN_STEP_LENGTH_S);
        } catch (SettingsError e) {
            this.minStepLength = 1.0;
        }
        try {
            maxStepLength = s.getDouble(MAX_STEP_LENGTH_S);
        } catch (SettingsError e) {
            this.maxStepLength = 1000.0;
        }

        if (this.minStepLength <= 0) {
            throw new SettingsError(
                    "TruncatedLevyWalkMobility: min_step_length (" + this.minStepLength + ") must be positive.");
        }
        if (this.maxStepLength < this.minStepLength) {
            throw new SettingsError("TruncatedLevyWalkMobility: max_step_length (" + this.maxStepLength
                    + ") must be greater than or equal to min_step_length (" + this.minStepLength + ").");
        }
        this.stepLengthRNG = new ParetoRNG(rng, this.alpha, this.minStepLength, this.maxStepLength);

    }

    /**
     * Copy constructor.
     *
     * @param tlw The TruncatedLevyWalkMobility prototype
     */
    protected TruncatedLevyWalkMobility(TruncatedLevyWalkMobility tlw) {
        super(tlw);
        alpha = tlw.alpha;
        minStepLength = tlw.minStepLength;
        maxStepLength = tlw.maxStepLength;
        this.stepLengthRNG = new ParetoRNG(rng, alpha, minStepLength, maxStepLength);

    }

    /**
     * Returns a possible (random) initial placement for a host.
     * Uses randomCoord() method (adapted from RandomWalk) to get a random location.
     *
     * @return Random position on the map.
     */
    @Override
    public Coord getInitialLocation() {
        assert rng != null : "MovementModel not initialized!";
        // Use the helper method randomCoord()
        Coord c = randomCoord();
        this.lastWaypoint = c; // Store the initial location
        return c;
    }
    /**
     * Generates and returns the next path (one Levy "flight").
     * The path starts from the current location (lastWaypoint).
     * Uses uniform speed and 'rejection sampling' boundary handling.
     *
     * @return A Path object representing one flight.
     */
    @Override
    public Path getPath() {
        assert this.lastWaypoint != null : "Initial location not set! Call getInitialLocation first.";
        assert rng != null : "MovementModel not initialized!";
        assert this.stepLengthRNG != null : "stepLengthRNG not initialized!";

        Coord oldLocation = this.lastWaypoint; // Lokasi awal

        double maxX = getMaxX();
        double maxY = getMaxY();

        Coord c = null; // Variabel untuk titik akhir valid

        while (true) {
            double stepLength = this.stepLengthRNG.getDouble();
            double direction = rng.nextDouble() * 2 * Math.PI;

            double x = oldLocation.getX() + stepLength * Math.cos(direction);
            double y = oldLocation.getY() + stepLength * Math.sin(direction);

            Coord potentialNewLocation = new Coord(x, y); // Buat objek Coord potensial

            // Cek batas (menggunakan maxX/maxY yang sudah diambil di luar loop)
            if (x >= 0 && x <= maxX && y >= 0 && y <= maxY) {
                c = potentialNewLocation; // Inisialisasi c saat valid
                break; // Keluar dari while loop
            }
        }


        Path p = new Path(generateSpeed());
        p.addWaypoint(c); // Waypoint tunggal: titik akhir

        // Perbarui lastWaypoint ke c
        this.lastWaypoint = c;

        return p; // Kembalikan path untuk dieksekusi simulator (dari lastWaypoint ke c)
    }

   

    /**
     * Creates a replicate of the movement model using the copy constructor.
     *
     * @return A new movement model with the same settings as this model.
     */
    @Override
    public MovementModel replicate() {
        // Use the copy constructor we defined
        return new TruncatedLevyWalkMobility(this);
    }

    /**
     * Returns a random coordinate within the simulation area bounds.
     * Uses inherited rng and world dimensions.
     *
     * @return A random coordinate.
     */
    protected Coord randomCoord() {
        return new Coord(rng.nextDouble() * getMaxX(),
                rng.nextDouble() * getMaxY());
    }

}


