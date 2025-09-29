package routing.carl_dtn.context.message;

/**
 * TODO:
 *
 * @author hendrowunga, University of Sanata Dharma
 * @version 1.0
 * @since 8/24/25
 */

import core.Message;

/**
 * Kelas data sederhana (POJO) untuk menampung pasangan Pesan dan Prioritasnya.
 * Berguna jika diperlukan untuk sorting atau operasi lain yang membutuhkan kedua data.
 * (Saat ini tidak digunakan secara aktif tetapi disimpan untuk skalabilitas).
 */
public class MessagePriority {
    public Message message;
    public double priority;

    public MessagePriority(Message message, double priority) {
        this.message = message;
        this.priority = priority;
    }
}