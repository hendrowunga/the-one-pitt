package movement;

import core.*;


/*
 * © 2025 hendrowunga, University of Sanata Dharma
 * Created on 5/30/25
 */

public class LevyWalk extends MovementModel {


    /**
     * Smaller alpha means more frequent long jumps. Typical range 0 < alpha <= 2.
     * Default value is {@link #DEFAULT_ALPHA}.
     */
    public static final String ALPHA_S = "alpha";
    /**
     * Minimum step length - setting id ({@value}).
     * Default value is {@link #DEFAULT_MIN_STEP}.
     */
    public static final String MIN_STEP_S = "minStep"; // Nama setting "minStep"
    /**
     * Scale factor for step length - setting id ({@value}).
     * Multiplies the value from the Pareto distribution.
     * Default value is {@link #DEFAULT_SCALE_FACTOR}.
     */
    public static final String SCALE_FACTOR_S = "scaleFactor"; // Nama setting "scaleFactor"

    public static final double DEFAULT_ALPHA = 1.5;
    public static final double DEFAULT_MIN_STEP = 0.1;
    public static final double DEFAULT_SCALE_FACTOR = 1.0;

    private double alpha;
    private double minStep;
    private double scaleFactor;

    private Coord currentPosition; // Lokasi node saat ini

    /**
     * Creates a new LevyWalk movement model based on a Settings object's settings.
     * Reads Levy Walk specific parameters (alpha, minStep, scaleFactor) from the
     * settings object provided by the simulator (scoped to the host group).
     *
     * @param settings The Settings object where the settings are read from (scoped to the host group).
     * @throws IllegalArgumentException if settings values are invalid.
     */
    public LevyWalk(Settings settings) {
        super(settings);

        this.alpha = settings.contains(ALPHA_S) ? settings.getDouble(ALPHA_S) : DEFAULT_ALPHA;
        this.minStep = settings.contains(MIN_STEP_S) ? settings.getDouble(MIN_STEP_S) : DEFAULT_MIN_STEP;
        this.scaleFactor = settings.contains(SCALE_FACTOR_S) ? settings.getDouble(SCALE_FACTOR_S) : DEFAULT_SCALE_FACTOR;

        // --- Validasi Pengaturan ---
        if (this.alpha <= 0) {
            throw new IllegalArgumentException(ALPHA_S + " must be > 0. Value was " + this.alpha);
        }
        if (this.minStep < 0) {
            throw new IllegalArgumentException(MIN_STEP_S + " must be >= 0. Value was " + this.minStep);
        }
        // Validasi scaleFactor bisa ditambahkan jika perlu (misal harus > 0)

        // currentPosition akan diinisialisasi oleh getInitialLocation() saat pertama kali dibutuhkan
        this.currentPosition = null;
    }

    /**
     * Copy constructor.
     * @param proto The LevyWalk prototype to copy.
     */
    protected LevyWalk(LevyWalk proto) {
        super(proto);
        this.alpha = proto.alpha;
        this.minStep = proto.minStep;
        this.scaleFactor = proto.scaleFactor;

        if (proto.currentPosition != null) {
            this.currentPosition = proto.currentPosition.clone();
        } else {
            this.currentPosition = null;
        }
    }

    /**
     * Returns a random initial placement for a host within world boundaries.
     * @return Initial random position.
     */
    @Override
    public Coord getInitialLocation() {
        assert rng != null : "MovementModel RNG not initialized!";

        double worldX = getMaxX(); // Diwarisi dari superclass (dibaca di super(settings))
        double worldY = getMaxY(); // Diwarisi dari superclass

        double x = rng.nextDouble() * worldX;
        double y = rng.nextDouble() * worldY;

        this.currentPosition = new Coord(x, y);
        return this.currentPosition.clone();
    }

    /**
     * Generates and returns the next path for the node (one step).
     * Uses a non-standard Pareto-derived step length and 'stay put' boundary handling.
     * @return A Path object for the next step.
     */
    @Override
    public Path getPath() {
        assert rng != null : "MovementModel RNG not initialized!";

        // Pastikan currentPosition terinisialisasi (seharusnya sudah di getInitialLocation atau panggilan sebelumnya)
        if (this.currentPosition == null) {
            getInitialLocation(); // Fallback jika somehow belum terinisialisasi
        }

        // Path ini akan dari currentPosition (sebelum update) ke currentPosition (setelah update/penanganan batas)
        Path path = new Path(generateSpeed()); // generateSpeed() diwarisi dan pakai pengaturan speed
        path.addWaypoint(this.currentPosition.clone()); // Tambahkan lokasi awal langkah sebagai waypoint pertama (opsional, gaya)

        // --- Pembangkitan Panjang Langkah (Non-Standar Pareto) ---
        double u = 0.0;
        while (u == 0.0) { // Pastikan u tidak nol
            u = rng.nextDouble();
        }
        // Menghasilkan nilai dari distribusi Pareto Tipe I dengan xm=1
        double paretoSample_xm1 = Math.pow(u, -1.0 / this.alpha);
        // Menggeser dan mengskalakan nilai sampel Pareto(alpha, 1)
        double stepLength = this.minStep + paretoSample_xm1 * this.scaleFactor;
        // --- End Pembangkitan Panjang Langkah ---


        // --- Pembangkitan Arah Acak Seragam ---
        double theta = rng.nextDouble() * 2.0 * Math.PI;
        // --- End Pembangkitan Arah ---

        // --- Hitung Lokasi Baru Potensial ---
        double dx = stepLength * Math.cos(theta);
        double dy = stepLength * Math.sin(theta);
        double potentialNewX = this.currentPosition.getX() + dx;
        double potentialNewY = this.currentPosition.getY() + dy;
        // --- End Hitung Lokasi Baru Potensial ---


        // --- Penanganan Batas ('Stay Put' if out of bounds) ---
        double worldMaxX = getMaxX(); // Diwarisi
        double worldMaxY = getMaxY(); // Diwarisi

        if (potentialNewX >= 0 && potentialNewX <= worldMaxX && potentialNewY >= 0 && potentialNewY <= worldMaxY) {
            // Jika lokasi potensial di dalam batas, update posisi node
            this.currentPosition.setLocation(potentialNewX, potentialNewY);
        }
        // Jika lokasi potensial di luar batas, currentPosition TIDAK DIUBAH, node tetap di tempat
        // --- End Penanganan Batas ---

        // Tambahkan lokasi node setelah penanganan batas (bisa lokasi baru atau lokasi lama)
        // sebagai waypoint kedua. Path akan dari lokasi awal langkah ke lokasi akhir langkah.
        path.addWaypoint(this.currentPosition.clone());

        return path; // Kembalikan path (segmen langkah tunggal)
    }

    /**
     * Returns the simulation time when the next path is available.
     * Returns 0, indicating no pause time between steps.
     * @return The current simulation time (ready immediately).
     */
    @Override
    public double nextPathAvailable() {
        return 0; // Tidak ada waktu diam
    }

    /**
     * Creates a replicate of this movement model instance.
     * @return A new LevyWalk instance with the same parameters.
     */
    @Override
    public LevyWalk replicate() {
        return new LevyWalk(this);
    }

    /**
     * Indicates whether this movement model is designed to have its full trajectory
     * history drawn on the GUI (if the global GUI option is enabled).
     * Levy Walk typically involves visualizing the full path.
     * @author hendrowunga
     * @return true
     */
    @Override
    public boolean shouldDrawTrajectoryHistory() {
        return true;
    }


}