package routing;

import core.*;

import java.util.List;

/**
 * @author : hend wunga
 */

public class BufferBatteryAwareRouter extends EnergyAwareRouter{

    public static  final String BATT_THRESHOLD_S="batteryThreshold";
    public static final String BUFF_THRESHOLD_S="bufferThreshold";

    private double batteryThreshold;
    private double bufferThreshold;


    /**
     * Constructor. Creates a new message router based on the settings in
     * the given Settings object.
     *
     * @param s The settings object
     */
    public BufferBatteryAwareRouter(Settings s) {
        super(s);

        batteryThreshold=s.getDouble(BATT_THRESHOLD_S);
        bufferThreshold=s.getDouble(BUFF_THRESHOLD_S);
    }

    /**
     * Copy constructor.
     *
     * @param r The router prototype where setting values are copied from
     */
    protected BufferBatteryAwareRouter(BufferBatteryAwareRouter r) {
        super(r);
        this.batteryThreshold=r.batteryThreshold;
        this.bufferThreshold=r.bufferThreshold;
    }

    @Override
    public void update() {
        // 1. Update status energi (mengurangi baterai karena scanning/idle)
        super.update();

        // 2. Cek apakah sedang transfer atau tidak bisa mulai transfer
        if (isTransferring() || !canStartTransfer()) {
            return;
        }

        // 3. LOGIKA THRESHOLD DIRI SENDIRI:
        // Jika baterai saya di bawah ambang batas, saya tidak mau mengirim pesan
        // agar baterai tidak habis total (mati).
        if (this.currentEnergy < this.batteryThreshold) {
            return;
        }

        // 4. Jalankan pengiriman pesan ke penerima akhir jika ada di sekitar
        if (exchangeDeliverableMessages() != null) {
            return;
        }

        // 5. Cek koneksi ke semua node di sekitar (replekasi pesan selektif)
        tryAllMessagesToAllConnections();
    }


    @Override
    protected Tuple<Message, Connection> tryMessagesForConnected(List<Tuple<Message, Connection>> tuples) {
        if (tuples.isEmpty()) {
            return null;
        }

        for (Tuple<Message, Connection> t : tuples) {
            Message m = t.getKey();
            Connection con = t.getValue();

            // Ambil node lawan
            DTNHost otherHost = con.getOtherNode(getHost());
            MessageRouter otherRouter = otherHost.getRouter();

            // Hitung rasio okupansi buffer lawan
            double peerBufferOccupancy = (double)otherRouter.getBufferOccupancy() / otherRouter.getBufferSize();

            // Cek Threshold: Hanya kirim jika buffer lawan belum melewati batas
            if (peerBufferOccupancy < this.bufferThreshold) {
                // Gunakan metode startTransfer dari ActiveRouter
                if (startTransfer(m, con) == RCV_OK) {
                    return t; // Berhasil kirim satu pesan, hentikan pencarian sesuai standar ActiveRouter
                }
            }
        }

        return null;
    }

    @Override
    public BufferBatteryAwareRouter replicate() {
        return new BufferBatteryAwareRouter(this);
    }

    @Override
    public String toString() {
        return super.toString() + " [Battery Thr: " + batteryThreshold +
                ", Buffer Thr: " + bufferThreshold + "]";
    }
}

