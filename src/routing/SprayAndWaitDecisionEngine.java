package routing;

import core.*; // Mengimpor kelas-kelas inti dari framework simulasi

import java.util.HashMap; // Mengimpor kelas HashMap untuk menyimpan informasi
import java.util.Map; // Mengimpor Map, yaitu cara menyimpan informasi berpasangan

/**
 * Implementasi strategi routing Spray and Wait seperti yang dijelaskan dalam paper
 * <I>Spray and Wait: An Efficient Routing Scheme for Intermittently
 * Connected Mobile Networks</I> oleh Thrasyvoulos Spyropoulus et al.
 * <p>
 * Strategi ini dirancang untuk jaringan yang terputus-putus di mana tidak selalu ada jalur langsung
 * antara sumber dan tujuan.
 * </p>
 * @author Hendrowunga, Sanata Dharma University, Network Laboratory
 */
public class SprayAndWaitDecisionEngine implements RoutingDecisionEngine {

    /** Identifier untuk pengaturan jumlah salinan awal (nilai = "nrofCopies") */
    public static final String NROF_COPIES = "nrofCopies";
    /** Identifier untuk pengaturan mode biner (nilai = "binaryMode") */
    public static final String BINARY_MODE = "binaryMode";
    /** Namespace untuk pengaturan SprayAndWait (nilai = "SprayAndWaitDecisionEngine") */
    public static final String SPRAYANDWAIT_NS = "SprayAndWaitDecisionEngine";
    /** Kunci properti pesan untuk menyimpan jumlah salinan (nilai = "SprayAndWaitDecisionEngine.copies") */
    public static final String MSG_COUNT_PROPERTY = SPRAYANDWAIT_NS + "." + "copies";

    protected int initialNrofCopies; // Jumlah awal salinan pesan yang akan di-spray
    protected boolean isBinary; // Apakah menggunakan mode biner atau tidak

    // Menyimpan waktu pembuatan setiap pesan. Kunci adalah ID pesan, dan nilainya adalah waktu pembuatan.
//    private final Map<String, Double> messageCreationTimes = new HashMap<>();

    /**
     * Konstruktor untuk kelas SprayAndWaitDecisionEngine.
     * @param s Objek Settings yang berisi pengaturan konfigurasi.
     */
    public SprayAndWaitDecisionEngine(Settings s) {
        Settings snwSettings = new Settings(SPRAYANDWAIT_NS); // Membuat objek Settings baru dengan namespace SprayAndWait

        // Logika untuk membaca jumlah salinan awal dari pengaturan:
        if (snwSettings.contains(NROF_COPIES)) { // Periksa apakah pengaturan NROF_COPIES ada
            initialNrofCopies = snwSettings.getInt(NROF_COPIES); // Jika ada, ambil nilainya
            System.out.println("Jumlah salinan awal (nrofCopies) diatur ke: " + initialNrofCopies); // Cetak nilai yang diambil dari pengaturan
        } else {
            initialNrofCopies = 2; // Jika tidak ada, gunakan 5 sebagai default
            System.out.println("Jumlah salinan awal (nrofCopies) tidak ditemukan. Menggunakan nilai default: " + initialNrofCopies); // Cetak pesan bahwa nilai default digunakan
        }
        /* Maksud: Kode ini membaca jumlah salinan awal yang akan di-spray dari file konfigurasi.
           Jika pengaturan "nrofCopies" tidak ditemukan, kode akan menggunakan nilai default 5.
           Jumlah salinan ini akan digunakan saat pesan baru dibuat untuk menentukan berapa banyak
           salinan pesan yang akan disebarkan ke jaringan. */

        // Logika untuk membaca mode biner:
        if (snwSettings.contains(BINARY_MODE)) { // Periksa apakah pengaturan BINARY_MODE ada
            isBinary = snwSettings.getBoolean(BINARY_MODE); // Jika ada, ambil nilainya
            System.out.println("Mode biner (binaryMode) diatur ke: " + isBinary); // Cetak nilai yang diambil dari pengaturan
        } else {
            isBinary = false; // Jika tidak ada, gunakan false sebagai default
            System.out.println("Mode biner (binaryMode) tidak ditemukan. Menggunakan nilai default: " + isBinary); // Cetak pesan bahwa nilai default digunakan
        }
        /* Maksud: Kode ini membaca apakah mode biner aktif atau tidak dari file konfigurasi.
           Jika pengaturan "binaryMode" tidak ditemukan, kode akan menggunakan nilai default false.
           Mode biner memengaruhi cara salinan pesan disebarkan di jaringan. */
    }

    /**
     * Copy constructor.
     * @param r Objek SprayAndWaitDecisionEngine yang akan disalin.
     */
    protected SprayAndWaitDecisionEngine(SprayAndWaitDecisionEngine r) {
        this.initialNrofCopies = r.initialNrofCopies; // Salin jumlah salinan awal
        this.isBinary = r.isBinary; // Salin mode biner
    }

    /**
     * Dipanggil saat koneksi naik antara host ini dan peer. Tidak ada implementasi khusus di sini.
     * @param thisHost Host yang menjalankan router ini (host lokal).
     * @param peer     Host peer (host lain) yang terhubung.
     */
    @Override
    public void connectionUp(DTNHost thisHost, DTNHost peer) {
        // Tidak ada tindakan khusus yang diperlukan saat koneksi naik.
    }
    /* Maksud: Method ini dipanggil ketika koneksi antara dua node dibuat.
       Dalam implementasi Spray and Wait ini, kita tidak perlu melakukan apa pun
       saat koneksi terbentuk, jadi method ini dibiarkan kosong. */

    /**
     * Dipanggil saat koneksi turun antara host ini dan peer. Tidak ada implementasi khusus di sini.
     * @param thisHost Host yang menjalankan router ini (host lokal).
     * @param peer     Host peer (host lain) yang koneksinya terputus.
     */
    @Override
    public void connectionDown(DTNHost thisHost, DTNHost peer) {
        // Tidak ada tindakan khusus yang diperlukan saat koneksi turun.
    }
    /* Maksud: Method ini dipanggil ketika koneksi antara dua node terputus.
       Seperti halnya connectionUp, tidak ada tindakan khusus yang perlu dilakukan
       dalam implementasi Spray and Wait ini, sehingga method ini dibiarkan kosong. */

    /**
     * Dipanggil untuk melakukan pertukaran informasi saat koneksi baru dibuat. Tidak ada implementasi khusus di sini.
     * @param con  Objek {@link Connection} yang merepresentasikan koneksi yang baru dibuat.
     * @param peer Host peer (host lain) yang terhubung melalui koneksi ini.
     */
    @Override
    public void doExchangeForNewConnection(Connection con, DTNHost peer) {
        // Tidak ada pertukaran informasi khusus yang diperlukan saat koneksi baru dibuat.
    }
    /* Maksud: Method ini dipanggil untuk memungkinkan pertukaran informasi routing
       antara dua perangkat yang baru terhubung. Dalam implementasi Spray and Wait ini, tidak ada
       pertukaran informasi yang diperlukan, sehingga method ini dibiarkan kosong. */

    /**
     * Dipanggil saat pesan baru dibuat. Mengatur properti jumlah salinan pesan dan menandai waktu pembuatan pesan.
     * @param m Pesan baru yang akan dipertimbangkan untuk routing.
     * @return True jika pesan diterima dan akan disimpan; False jika terjadi kesalahan.
     */
    @Override
    public boolean newMessage(Message m) {
        try {
            m.addProperty(MSG_COUNT_PROPERTY, initialNrofCopies); // Tambahkan properti jumlah salinan ke pesan dengan nilai awal
        } catch (SimError e) {
            System.out.println("Error adding property to message: " + e.getMessage()); // Cetak pesan kesalahan jika gagal menambahkan properti
            return false; // Kembalikan false untuk menandakan kegagalan
        }
        /* Maksud: Kode ini mencoba menambahkan properti "copies" ke pesan dengan nilai
           initialNrofCopies. Jika gagal, kode akan mencetak pesan kesalahan dan
           mengembalikan false, yang menandakan bahwa pesan tidak dapat diproses. */

//        messageCreationTimes.put(m.getId(), SimClock.getTime()); // Simpan waktu pembuatan pesan
        /* Maksud: Kode ini menyimpan waktu pembuatan pesan dalam map messageCreationTimes.
           Ini digunakan untuk menghitung TTL (Time-To-Live) pesan nanti. */

        return true; // Kembalikan true untuk menandakan bahwa pesan diterima dan akan disimpan
    }
    /* Maksud: Method ini dipanggil ketika pesan baru dibuat di node ini.
       Tujuannya adalah untuk menginisialisasi properti pesan yang diperlukan oleh
       algoritma Spray and Wait, yaitu jumlah salinan yang tersedia untuk pesan tersebut.
       Waktu pembuatan pesan juga disimpan untuk digunakan dalam perhitungan TTL. */

    /**
     * Menentukan apakah host tertentu adalah tujuan akhir dari pesan.
     * @param m     Pesan yang baru diterima.
     * @param aHost Host yang akan diperiksa apakah merupakan tujuan akhir.
     * @return True jika host adalah tujuan akhir; False jika bukan.
     */
    @Override
    public boolean isFinalDest(Message m, DTNHost aHost) {
        return m.getTo() == aHost; // Periksa apakah tujuan pesan sama dengan host yang diberikan
    }
     /* Maksud: Method ini memeriksa apakah host yang diberikan (aHost) adalah tujuan
        akhir dari pesan m. Ini adalah bagian penting dari logika routing, karena
        jika host adalah tujuan akhir, pesan harus dikirimkan kepadanya. */

    /**
     * Menentukan apakah pesan yang baru diterima dari peer harus disimpan di buffer dan diteruskan lebih lanjut.
     * @param m        Pesan yang baru diterima dari peer.
     * @param thisHost Host yang menjalankan router ini (host lokal).
     * @return True jika pesan harus disimpan; False jika tidak.
     */
    @Override
    public boolean shouldSaveReceivedMessage(Message m, DTNHost thisHost) {
        boolean shouldSave = false; // Apakah pesan harus disimpan? Default: tidak

//        // Pertama, periksa apakah pesan sudah kadaluarsa
//        if (isMessageExpired(m)) {
//            // Jika pesan sudah kadaluarsa, jangan simpan
//            shouldSave = false; // Atur shouldSave ke false
//        } else {
            // Jika pesan belum kadaluarsa, lanjutkan pemeriksaan lain

            // Periksa apakah host ini sudah memiliki pesan dengan ID yang sama
            if (thisHost.getRouter().hasMessage(m.getId())) {
                // Jika host ini sudah memiliki pesan yang sama, jangan simpan
                shouldSave = false; // Atur shouldSave ke false
            } else {
                // Jika host ini belum memiliki pesan yang sama, lanjutkan pemeriksaan terakhir

                // Periksa apakah pesan berasal dari host ini sendiri
                if (m.getFrom() == thisHost) {
                    // Jika pesan berasal dari host ini sendiri, jangan simpan (untuk menghindari loop)
                    shouldSave = false; // Atur shouldSave ke false
                } else {
                    // Jika pesan bukan berasal dari host ini sendiri, simpan pesan
                    shouldSave = true; // Atur shouldSave ke true
                }
            }
//        }

        return shouldSave; // Kembalikan nilai shouldSave (true atau false)
    }
     /* Maksud: Method ini menentukan apakah pesan yang diterima dari node lain
        harus disimpan di buffer node ini atau tidak. Tujuannya adalah untuk mencegah
        penyimpanan pesan duplikat dan pesan yang sudah kadaluarsa. */

    /**
     * Menentukan apakah pesan harus dikirim ke host lain.
     * @param m         Pesan yang akan dievaluasi untuk pengiriman.
     * @param otherHost Host peer (host lain) yang berpotensi menjadi tujuan pengiriman.
     * @param thisHost  Host yang menjalankan router ini (host lokal).
     * @return True jika pesan harus dikirim; False jika tidak.
     */
    @Override
    public boolean shouldSendMessageToHost(Message m, DTNHost otherHost, DTNHost thisHost) {
        boolean shouldSend = false; // Apakah pesan harus dikirim? Default: tidak

        Integer copies = (Integer) m.getProperty(MSG_COUNT_PROPERTY); // Dapatkan jumlah salinan pesan

        if (copies != null) { // Periksa apakah jumlah salinan tidak null
            if (copies > 1) { // Periksa apakah jumlah salinan lebih besar dari 1
                shouldSend = true; // Jika ya, atur shouldSend ke true (kirim pesan)
            } else {
                shouldSend = false; // Jika tidak, atur shouldSend ke false (jangan kirim pesan)
            }
        } else {
            shouldSend = false; // Jika jumlah salinan null, atur shouldSend ke false (jangan kirim pesan)
        }

        return shouldSend; // Kembalikan nilai shouldSend
    }
    /* Maksud: Method ini menentukan apakah pesan harus dikirim ke host lain atau tidak.
       Dalam strategi Spray and Wait, pesan hanya dikirim jika perangkat memiliki lebih
       dari satu salinan pesan. Ini adalah bagian dari fase "Spray" dalam strategi ini. */

    /**
     * Menentukan apakah pesan yang telah berhasil dikirim harus dihapus dari buffer.
     * @param m         Pesan yang telah berhasil dikirim.
     * @param otherHost Host peer (host lain) yang menerima pesan.
     * @return True jika pesan harus dihapus; False jika tidak.
     */
    @Override
    public boolean shouldDeleteSentMessage(Message m, DTNHost otherHost) {
        // Ambil thisHost dari properti pesan (ditambahkan oleh DecisionEngineRouter)
        DTNHost thisHost = (DTNHost) m.getProperty("thisHost");
        if (thisHost == null) {
            System.out.println("Error: thisHost tidak ditemukan dalam properti pesan!"); // Jika thisHost tidak ditemukan, tampilkan pesan kesalahan
            return false; // Jangan hapus pesan
        }

        boolean deleteMessage = false; // Apakah pesan harus dihapus? Default: tidak

//        // Periksa apakah pesan sudah mencapai tujuan akhir
//        if (isFinalDest(m, otherHost)) {
//            messageCreationTimes.remove(m.getId()); // Hapus waktu pembuatan pesan dari map
//            deleteMessage = true; // Hapus pesan karena sudah sampai tujuan
//        } else if (isMessageExpired(m)) { // Periksa apakah pesan sudah kadaluarsa
//            messageCreationTimes.remove(m.getId()); // Hapus waktu pembuatan pesan dari map
//            deleteMessage = true; // Hapus pesan karena sudah kadaluarsa
//        } else { // Jika pesan belum mencapai tujuan akhir dan belum kadaluarsa
            Integer copies = (Integer) m.getProperty(MSG_COUNT_PROPERTY); // Dapatkan jumlah salinan pesan
            if (copies == null) {
                deleteMessage = true; // Jika tidak ada salinan, hapus pesan
            } else {
                if (copies <= 0) {
                    deleteMessage = true; //JIka copy kurang dari sama dengan 0 , maka hapus
                } else {
                    deleteMessage = false; // Jika tidak, jangan hapus pesan
                }

            }
//        }
        return deleteMessage;
        /*Maksud: Method ini menentukan apakah pesan yang baru saja dikirim harus
           dihapus dari memori perangkat ini atau tidak. Pesan dihapus berdasarkan beberapa
           kondisi, termasuk apakah pesan telah mencapai tujuan akhir, apakah TTL-nya
           telah kedaluwarsa, dan apakah masih ada salinan pesan yang tersisa. */
    }

    /**
     * Menentukan apakah pesan lama harus dihapus.
     * @param m                Pesan yang dianggap sudah lama.
     * @param hostReportingOld Host peer (host lain) yang melaporkan bahwa pesan tersebut sudah lama.
     * @return True jika pesan harus dihapus; False jika tidak.
     */
    @Override
    public boolean shouldDeleteOldMessage(Message m, DTNHost hostReportingOld) {
        boolean deleteMessage = false; // Inisialisasi variable delete message
        if (isMessageExpired(m)) { //apakah message tersebut sudah di nyatakan kadaluarsa
            deleteMessage = true;// Hapus pesan jika sudah kadaluarsa
        }else{
            deleteMessage = false;//jangan di hapus
        }
        return deleteMessage;
           /* Maksud: Method ini memeriksa apakah pesan m telah kedaluwarsa dengan memanggil metode
              isMessageExpired(m). Jika metode ini mengembalikan true (yang berarti
              pesan telah kedaluwarsa), kode mengembalikan true, yang berarti pesan
              harus dihapus. Jika tidak, kode mengembalikan false, yang berarti pesan
              tidak boleh dihapus. */
    }

    /**
     * Memperbarui status decision engine. Tidak ada implementasi khusus di sini.
     * @param thisHost Host yang menjalankan router ini (host lokal).
     */
    @Override
    public void update(DTNHost thisHost) {
        // Tidak ada tindakan pembaruan khusus yang diperlukan.
    }
     /* Maksud: Method ini dipanggil secara berkala untuk memberi kesempatan kepada
        DecisionEngine untuk memperbarui status internalnya. Dalam implementasi
        Spray and Wait ini, tidak ada pembaruan status khusus yang diperlukan,
        sehingga method ini dibiarkan kosong. */

    /**
     * Memeriksa apakah pesan sudah kedaluwarsa berdasarkan TTL.
     * @param m Pesan yang akan diperiksa.
     * @return true jika pesan sudah kedaluwarsa, false jika tidak.
     */
    private boolean isMessageExpired(Message m) {
//        Double creationTime = messageCreationTimes.get(m.getId()); // Dapatkan waktu pembuatan pesan
//        boolean messageExpired=false; //inisialisasi variable messageExpired
//        if (creationTime == null) { //apakah creation Time nya kosong
//            messageExpired = false;// Jika tidak ada waktu pembuatan, anggap pesan belum kadaluarsa (atau gunakan strategi lain)
//        }else{
//            double age = SimClock.getTime() - creationTime; // Hitung usia pesan
//            if (age > m.getTtl()) { // Periksa apakah usia pesan melebihi TTL
//                messageExpired = true;// Jika ya, kembalikan true (pesan kadaluarsa)
//            }else{
//                messageExpired = false;// Jika tidak, kembalikan false (pesan belum kadaluarsa)
//            }
//        }
        return false;
          /* Maksud: Method ini memeriksa apakah pesan m telah kedaluwarsa atau tidak.
             Untuk melakukan ini, kode pertama-tama mencoba mendapatkan waktu
             pembuatan pesan dari map messageCreationTimes menggunakan ID pesan
             sebagai kunci. Jika waktu pembuatan tidak ditemukan (yaitu,
             creationTime bernilai null), kode mengembalikan false, yang berarti
             pesan dianggap belum kedaluwarsa. Jika waktu pembuatan ditemukan,
             kode menghitung usia pesan dengan mengurangi waktu pembuatan dari
             waktu simulasi saat ini (SimClock.getTime()). Kemudian, kode
             membandingkan usia pesan dengan TTL pesan (m.getTtl()). Jika usia
             pesan lebih besar dari TTL, kode mengembalikan true, yang berarti
             pesan telah kedaluwarsa. Jika tidak, kode mengembalikan false,
             yang berarti pesan belum kedaluwarsa. */
    }

    /**
     * Membuat duplikat (replika) dari Decision Engine ini. Tidak ada implementasi di sini
     * @return Salinan baru dari Decision Engine ini.
     */
    @Override
    public RoutingDecisionEngine replicate() {
        return new SprayAndWaitDecisionEngine(this);
    }
    /* Maksud: Kode ini membuat dan mengembalikan salinan (replika) dari objek
       SprayAndWaitDecisionEngine saat ini. Ini penting karena setiap
       node dalam simulasi akan memiliki instance SprayAndWaitDecisionEngine
       sendiri, dan kita ingin memastikan bahwa setiap instance memiliki
       status yang independen. */
}