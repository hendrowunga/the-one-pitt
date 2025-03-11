package routing;

import core.*;

/**
 * Mendefinisikan antarmuka (interface) antara `DecisionEngineRouter` dan objek yang membuat keputusan routing.
 * <p>
 * Interface ini mendefinisikan method-method yang digunakan oleh `DecisionEngineRouter` untuk
 * meminta keputusan tentang bagaimana pesan harus dirutekan. Implementasi dari interface ini
 * (disebut "Decision Engine") berisi logika routing yang spesifik (misalnya, Epidemic,
 * Spray and Wait, dll.).
 * </p>
 * @author @author Hendrowunga, Sanata Dharma University, Network Laboratory
 */
public interface RoutingDecisionEngine {

	/**
	 * Dipanggil saat koneksi (hubungan langsung) terbentuk antara host ini dan peer (host lain).
	 * <p>
	 * Method ini memberi tahu Decision Engine bahwa koneksi baru telah terbentuk. Decision Engine
	 * dapat menggunakan informasi ini untuk memperbarui tabel routingnya, memulai pertukaran informasi,
	 * atau melakukan tindakan lain yang sesuai.
	 * </p>
	 * <p>
	 * Penting: `doExchangeForNewConnection()` mungkin dipanggil *sebelum* method ini dipanggil,
	 * untuk memungkinkan pertukaran informasi awal sebelum koneksi sepenuhnya siap.
	 * </p>
	 * @param thisHost Host yang menjalankan router ini (host lokal).
	 * @param peer     Host peer (host lain) yang terhubung.
	 */
	public void connectionUp(DTNHost thisHost, DTNHost peer);

	/**
	 * Dipanggil saat koneksi (hubungan langsung) terputus antara host ini dan peer (host lain).
	 * <p>
	 * Method ini memberi tahu Decision Engine bahwa koneksi telah hilang. Decision Engine
	 * dapat menggunakan informasi ini untuk menghapus rute yang tidak valid, memperbarui tabel routing,
	 * atau melakukan tindakan lain yang sesuai.
	 * </p>
	 * @param thisHost Host yang menjalankan router ini (host lokal).
	 * @param peer     Host peer (host lain) yang koneksinya terputus.
	 */
	public void connectionDown(DTNHost thisHost, DTNHost peer);

	/**
	 * Dipanggil sekali untuk setiap koneksi yang terbentuk, untuk memungkinkan dua Decision Engine
	 * di kedua ujung koneksi bertukar dan memperbarui informasi routing mereka secara bersamaan.
	 * <p>
	 * Method ini penting untuk menghindari masalah di mana satu host memperbarui informasinya
	 * berdasarkan informasi yang *baru* dari host lain, sementara host lain menggunakan informasi
	 * yang *lama*. Dengan melakukan pertukaran secara bersamaan, kedua host menggunakan informasi
	 * yang konsisten satu sama lain.
	 * </p>
	 * @param con  Objek {@link Connection} yang merepresentasikan koneksi yang baru dibuat.
	 * @param peer Host peer (host lain) yang terhubung melalui koneksi ini.
	 */
	public void doExchangeForNewConnection(Connection con, DTNHost peer);

	/**
	 * Dipanggil saat pesan baru dibuat di host ini.
	 * <p>
	 * Method ini memberi kesempatan kepada Decision Engine untuk memeriksa pesan dan memutuskan
	 * apakah pesan tersebut harus diterima dan dirutekan lebih lanjut, atau ditolak (dibuang).
	 * </p>
	 * <p>
	 * Penting: Method ini hanya dipanggil untuk pesan yang *berasal* dari host ini,
	 * bukan pesan yang diterima dari peer.
	 * </p>
	 * @param m Pesan baru yang akan dipertimbangkan untuk routing.
	 * @return `true` jika pesan harus diterima dan dirutekan, `false` jika pesan harus dibuang.
	 */
	public boolean newMessage(Message m);

	/**
	 * Menentukan apakah host tertentu (aHost) adalah tujuan akhir dari pesan tertentu (m).
	 * <p>
	 * Method ini dipanggil saat pesan baru diterima di router ini. Decision Engine harus
	 * memeriksa alamat tujuan pesan dan membandingkannya dengan alamat host yang diberikan
	 * untuk menentukan apakah host tersebut adalah tujuan akhir.
	 * </p>
	 * @param m     Pesan yang baru diterima.
	 * @param aHost Host yang akan diperiksa apakah merupakan tujuan akhir.
	 * @return `true` jika host yang diberikan adalah tujuan akhir dari pesan, `false` jika bukan.
	 */
	public boolean isFinalDest(Message m, DTNHost aHost);

	/**
	 * Menentukan apakah pesan yang diterima dari peer harus disimpan dalam penyimpanan pesan
	 * host ini dan dirutekan lebih lanjut.
	 * <p>
	 * Method ini memungkinkan Decision Engine untuk menerapkan kebijakan penyimpanan pesan.
	 * Misalnya, Decision Engine dapat memilih untuk tidak menyimpan pesan yang sudah diterima
	 * sebelumnya, pesan yang TTL-nya sudah habis, atau pesan yang berasal dari host yang tidak
	 * dipercaya.
	 * </p>
	 * @param m        Pesan yang baru diterima dari peer.
	 * @param thisHost Host yang menjalankan router ini (host lokal).
	 * @return `true` jika pesan harus disimpan dan dirutekan lebih lanjut, `false` jika tidak.
	 */
	public boolean shouldSaveReceivedMessage(Message m, DTNHost thisHost);

	/**
	 * Menentukan apakah pesan tertentu (m) harus dikirim ke host tertentu (otherHost).
	 * <p>
	 * Method ini merupakan inti dari logika routing. Decision Engine harus mempertimbangkan
	 * berbagai faktor, seperti konektivitas jaringan, jarak ke tujuan, jumlah salinan pesan yang
	 * sudah ada di jaringan, dan kebijakan routing lainnya, untuk menentukan apakah pengiriman
	 * pesan ke host yang diberikan akan meningkatkan probabilitas pengiriman atau mengurangi overhead.
	 * </p>
	 * @param m         Pesan yang akan dievaluasi untuk pengiriman.
	 * @param otherHost Host peer (host lain) yang berpotensi menjadi tujuan pengiriman.
	 * @param thisHost  Host yang menjalankan router ini (host lokal).
	 * @return `true` jika pesan harus dikirim ke host yang diberikan, `false` jika tidak.
	 */
	public boolean shouldSendMessageToHost(Message m, DTNHost otherHost, DTNHost thisHost);

	/**
	 * Dipanggil setelah pesan berhasil dikirim ke peer, untuk menentukan apakah pesan tersebut
	 * sekarang harus dihapus dari penyimpanan pesan host ini.
	 * <p>
	 * Method ini memungkinkan Decision Engine untuk menerapkan kebijakan penyimpanan pesan.
	 * Misalnya, Decision Engine dapat memilih untuk menghapus pesan setelah pesan tersebut berhasil
	 * dikirim ke tujuan akhir, atau setelah sejumlah salinan pesan telah disebarkan ke jaringan.
	 * </p>
	 * @param m         Pesan yang telah berhasil dikirim.
	 * @param otherHost Host peer (host lain) yang menerima pesan.
	 * @return `true` jika pesan harus dihapus dari penyimpanan pesan host ini, `false` jika tidak.
	 */
	public boolean shouldDeleteSentMessage(Message m, DTNHost otherHost);

	/**
	 * Dipanggil jika upaya pengiriman pesan ke peer gagal dan kode pengembalian menunjukkan
	 * bahwa pesan tersebut sudah lama atau sudah dikirim sebelumnya. Dalam kasus ini,
	 * pesan mungkin perlu dihapus.
	 * <p>
	 * Method ini memberi kesempatan kepada Decision Engine untuk membersihkan pesan-pesan
	 * yang tidak lagi berguna, berdasarkan laporan dari peer lain.
	 * </p>
	 * @param m              Pesan yang dianggap sudah lama.
	 * @param hostReportingOld Host peer (host lain) yang melaporkan bahwa pesan tersebut sudah lama.
	 * @return `true` jika pesan harus dihapus dari penyimpanan pesan host ini, `false` jika tidak.
	 */
	public boolean shouldDeleteOldMessage(Message m, DTNHost hostReportingOld);

	/**
	 * Dipanggil secara berkala untuk memberi kesempatan kepada Decision Engine untuk memperbarui
	 * status internalnya.
	 * <p>
	 * Method ini dapat digunakan untuk melakukan pembersihan berkala, memperbarui tabel routing,
	 * atau melakukan tindakan pemeliharaan lainnya.
	 * </p>
	 * @param thisHost Host yang menjalankan router ini (host lokal).
	 */
	public void update(DTNHost thisHost);

	/**
	 * Membuat duplikat (replika) dari Decision Engine ini.
	 * <p>
	 * Method ini penting karena setiap host dalam simulasi akan memiliki instance Decision Engine sendiri.
	 * </p>
	 * @return Salinan baru dari Decision Engine ini.
	 */
	public RoutingDecisionEngine replicate();
}