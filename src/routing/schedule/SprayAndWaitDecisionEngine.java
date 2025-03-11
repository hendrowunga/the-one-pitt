//package routing.schedule;
//
//import core.*;
//import routing.RoutingDecisionEngine;
//
//import java.util.HashMap;
//import java.util.Map;
//
///**
// * Implementasi strategi routing Spray and Wait dengan dukungan TTL.
// */
//public class SprayAndWaitDecisionEngine implements RoutingDecisionEngine {
//
//    /**
//     * identifier for the initial number of copies setting ({@value})
//     */
//    public static final String NROF_COPIES = "nrofCopies";
//    /**
//     * identifier for the binary-mode setting ({@value})
//     */
//    public static final String BINARY_MODE = "binaryMode";
//    /**
//     * SprayAndWait router's settings name space ({@value})
//     */
//    public static final String SPRAYANDWAIT_NS = "SprayAndWaitDecisionEngine";
//    /**
//     * Message property key
//     */
//    public static final String MSG_COUNT_PROPERTY = SPRAYANDWAIT_NS + "." +
//            "copies";
//
//    protected int initialNrofCopies;
//    protected boolean isBinary;
//
//    // Menyimpan waktu pembuatan setiap pesan.
//    private final Map<String, Double> messageCreationTimes = new HashMap<>();
//
//    public SprayAndWaitDecisionEngine(Settings s) {
//        Settings snwSettings = new Settings(SPRAYANDWAIT_NS);
//
//        // Baca jumlah salinan awal, gunakan 5 jika tidak ada.
//        initialNrofCopies = snwSettings.contains(NROF_COPIES) ?
//                snwSettings.getInt(NROF_COPIES) : 5;
//
//        // Baca mode biner, gunakan false jika tidak ada.
//        isBinary = snwSettings.contains(BINARY_MODE) ?
//                snwSettings.getBoolean(BINARY_MODE) : false;
//    }
//
//    /**
//     * Copy constructor.
//     */
//    protected SprayAndWaitDecisionEngine(SprayAndWaitDecisionEngine r) {
//        this.initialNrofCopies = r.initialNrofCopies;
//        this.isBinary = r.isBinary;
//    }
//
//    @Override
//    public void connectionUp(DTNHost thisHost, DTNHost peer) {}
//
//    @Override
//    public void connectionDown(DTNHost thisHost, DTNHost peer) {}
//
//    @Override
//    public void doExchangeForNewConnection(Connection con, DTNHost peer) {}
//
//    @Override
//    public boolean newMessage(Message m) {
//        try {
//            m.addProperty(MSG_COUNT_PROPERTY, initialNrofCopies);
//        } catch (SimError e) {
//            System.out.println("Error adding property to message: " + e.getMessage());
//            return false;
//        }
//        messageCreationTimes.put(m.getId(), SimClock.getTime());
//
//        return true; // Pesan diterima dan akan disimpan
//    }
//
//    @Override
//    public boolean isFinalDest(Message m, DTNHost aHost) {
//        return m.getTo() == aHost;
//    }
//
//    @Override
//    public boolean shouldSaveReceivedMessage(Message m, DTNHost thisHost) {
//        return !isMessageExpired(m) && !thisHost.getRouter().hasMessage(m.getId()) && m.getFrom() != thisHost;
//    }
//
//    @Override
//    public boolean shouldSendMessageToHost(Message m, DTNHost otherHost, DTNHost thisHost) {
//        Integer copies = (Integer) m.getProperty(MSG_COUNT_PROPERTY);
//        return copies != null && copies > 1;
//    }
//
//    @Override
//    public boolean shouldDeleteSentMessage(Message m, DTNHost otherHost) {
//        // Ambil thisHost dari properti pesan
//        DTNHost thisHost = (DTNHost) m.getProperty("thisHost");
//        if (thisHost == null) {
//            System.out.println("Error: thisHost tidak ditemukan dalam properti pesan!");
//            return false; // Atau strategi penanganan kesalahan lainnya
//        }
//
//        if (isFinalDest(m, otherHost) || isMessageExpired(m)) {
//            messageCreationTimes.remove(m.getId());
//            return true;
//        }
//
//        Integer copies = (Integer) m.getProperty(MSG_COUNT_PROPERTY);
//        if (copies == null || copies <= 0) return true;
//
//        try {
//            if (isBinary) {
//                int copiesToGive = copies / 2;
//                if (copiesToGive > 0) {
//                    try {
//                        m.updateProperty(MSG_COUNT_PROPERTY, copies - copiesToGive);
//                        Message clonedMessage = m.replicate();
//                        clonedMessage.addProperty(MSG_COUNT_PROPERTY, copiesToGive);
//                        otherHost.getRouter().receiveMessage(clonedMessage, thisHost);
//                    } catch (SimError e) {
//                        System.out.println("Error updating property in Binary Mode: " + e.getMessage());
//                    }
//                }
//            } else {
//                try {
//                    m.updateProperty(MSG_COUNT_PROPERTY, copies - 1);
//                } catch (SimError e) {
//                    System.out.println("Error updating property: " + e.getMessage());
//                }
//            }
//
//        } catch (SimError e) {
//            System.out.println("Error updating property: " + e.getMessage());
//        }
//        return false;
//    }
//
//    @Override
//    public boolean shouldDeleteOldMessage(Message m, DTNHost hostReportingOld) {
//        return isMessageExpired(m);
//    }
//
//    @Override
//    public void update(DTNHost thisHost) {}
//
//    /**
//     * Memeriksa apakah pesan sudah kedaluwarsa berdasarkan TTL.
//     * @param m Pesan yang akan diperiksa.
//     * @return true jika pesan sudah kedaluwarsa, false jika tidak.
//     */
//    private boolean isMessageExpired(Message m) {
//        Double creationTime = messageCreationTimes.get(m.getId());
//        return creationTime != null && SimClock.getTime() - creationTime > m.getTtl();
//    }
//
//    @Override
//    public RoutingDecisionEngine replicate() {
//        return new SprayAndWaitDecisionEngine(this);
//    }
//}