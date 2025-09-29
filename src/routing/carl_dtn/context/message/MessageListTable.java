package routing.carl_dtn.context.message;

import core.Message;

import java.util.HashMap;
import java.util.Map;

/**
 * TODO:
 *
 * @author hendrowunga, University of Sanata Dharma
 * @version 1.0
 * @since 8/24/25
 */

/**
 * Kelas ini mengelola prioritas pesan secara real-time.
 * Setiap pesan dalam buffer router akan memiliki skor prioritas yang dihitung
 * oleh FuzzyMessageEvaluator dan disimpan di sini.
 */
public class MessageListTable {
    private final Map<String, Double> messagePriorityMap;

    public MessageListTable() {
        this.messagePriorityMap = new HashMap<>();
    }

    /**
     * Memperbarui atau menambahkan skor prioritas untuk sebuah pesan.
     * @param message Pesan yang akan diberi skor.
     * @param priority Skor prioritas (0.0 - 1.0).
     */
    public void updateMessagePriority(Message message, double priority) {
        messagePriorityMap.put(message.getId(), priority);
    }

    /**
     * Mendapatkan skor prioritas dari sebuah pesan.
     * @param message Pesan yang ingin diketahui prioritasnya.
     * @return Skor prioritas, atau 0.0 jika tidak ditemukan.
     */
    public double getPriority(Message message) {
        return messagePriorityMap.getOrDefault(message.getId(), 0.0);
    }

    /**
     * Menghapus entri prioritas pesan, biasanya saat pesan dihapus dari buffer.
     * @param message Pesan yang akan dihapus.
     */
    public void removeMessage(Message message) {
        messagePriorityMap.remove(message.getId());
    }
}