package routing.rl;

/*
 * © 2025 hendrowunga, University of Sanata Dharma
 * Created on 6/7/25
 */
import core.DTNHost;
import core.Message;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Merepresentasikan State yang dilihat oleh agen QLearningRouter.
 * Dalam implementasi ini, state ditentukan oleh tingkat keterisian buffer
 * dari node peer.
 */
public class RLState {

    private final int peerBufferCategory;
    private final int destinationAffinity; // 0 = Beda Grup, 1 = Sama Grup

    // Pola Regex untuk mengekstrak bagian non-numerik dari nama host
    private static final Pattern groupIdPattern = Pattern.compile("^(\\D+).*");

    public RLState(DTNHost self, DTNHost peer, Message msg) {
        // 1. Kategori Buffer (sama seperti sebelumnya)
        double bufferSize = peer.getRouter().getBufferSize();
        double freeBuffer = peer.getRouter().getFreeBufferSize();
        if (bufferSize == 0) {
            this.peerBufferCategory = 3;
        } else {
            double occupancy = (bufferSize - freeBuffer) / bufferSize;
            if (occupancy < 0.25) this.peerBufferCategory = 0;
            else if (occupancy < 0.50) this.peerBufferCategory = 1;
            else if (occupancy < 0.75) this.peerBufferCategory = 2;
            else this.peerBufferCategory = 3;
        }

        // 2. Afinitas Tujuan (Dimensi Baru dengan Solusi)
        DTNHost finalDestination = msg.getTo();

        // PERBAIKAN: Panggil metode helper baru untuk mendapatkan Group ID
        String peerGroupId = getGroupIdFromHost(peer);
        String destGroupId = getGroupIdFromHost(finalDestination);

        if (peerGroupId != null && peerGroupId.equals(destGroupId)) {
            this.destinationAffinity = 1; // Peer satu grup dengan tujuan, kandidat bagus!
        } else {
            this.destinationAffinity = 0; // Peer beda grup, kandidat kurang bagus.
        }
    }

    /**
     * Metode helper untuk mengekstrak Group ID dari nama DTNHost.
     * Misal: "A50" -> "A", "car23" -> "car"
     * @param host Host yang akan diekstrak Group ID-nya.
     * @return String Group ID, atau null jika tidak ditemukan.
     */
    private String getGroupIdFromHost(DTNHost host) {
        if (host == null) {
            return null;
        }
        Matcher matcher = groupIdPattern.matcher(host.toString());
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null; // Seharusnya tidak pernah terjadi jika nama host mengikuti pola
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RLState rlState = (RLState) o;
        return peerBufferCategory == rlState.peerBufferCategory &&
                destinationAffinity == rlState.destinationAffinity;
    }

    @Override
    public int hashCode() {
        return Objects.hash(peerBufferCategory, destinationAffinity);
    }

    @Override
    public String toString() {
        String affinityStr = (destinationAffinity == 1) ? "SameGroup" : "DiffGroup";
        return "RLState{bufCat=" + peerBufferCategory + ", aff=" + affinityStr + "}";
    }
}