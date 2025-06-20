//package report;
//
//import core.DTNHost;
//import core.Message;
//import core.MessageListener;
//import routing.rl.QLearningRouter;
//import routing.rl.RLState;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Map;
//
///**
// * Report listener yang DIDESAIN KHUSUS untuk menganalisis kinerja dan
// * pembelajaran dari QLearningRouter (Model Baseline/Terpusat).
// * Menggunakan perhitungan delivery ratio yang akurat.
// *
// * © 2025 hendrowunga, University of Sanata Dharma
// */
//public class RLRewardReporter extends Report implements MessageListener {
//
//    private int nrofCreated;
//    private int successfulDeliveries;
//    private int failedDeliveries;
//    private List<String> successfulRoutes;
//    private List<String> failedRoutes;
//    private static final int MAX_EXAMPLE_ROUTES = 5;
//
//    public RLRewardReporter() {
//        super();
//        init();
//    }
//
//    @Override
//    public void init() {
//        super.init();
//        this.successfulRoutes = new ArrayList<>();
//        this.failedRoutes = new ArrayList<>();
//        this.nrofCreated = 0;
//        this.successfulDeliveries = 0;
//        this.failedDeliveries = 0;
//    }
//
//    @Override
//    public void newMessage(Message m) {
//        if (isWarmupID(m.getId())) {
//            return;
//        }
//        this.nrofCreated++;
//    }
//
//    @Override
//    public void messageTransferred(Message m, DTNHost from, DTNHost to, boolean firstDelivery) {
//        if (isWarmupID(m.getId())) { return; }
//
//        if (firstDelivery) {
//            this.successfulDeliveries++;
//            if (this.successfulRoutes.size() < MAX_EXAMPLE_ROUTES) {
//                this.successfulRoutes.add(formatRoute(m, "SUCCESS"));
//
//            }
//
//            double reward = 1.0;
//            // Memeriksa instance dari QLearningRouter
//            if (from.getRouter() instanceof QLearningRouter) {
//                QLearningRouter router = (QLearningRouter) from.getRouter();
//                RLState state = new RLState(from, to, m);
//                // Memanggil metode updateQValue dari instance manapun akan memperbarui Q-Table static yang sama
//                router.updateQValue(state, QLearningRouter.ACTION_FORWARD, reward);
//            }
//        }
//    }
//
//    @Override
//    public void messageDeleted(Message m, DTNHost where, boolean dropped) {
//        if (isWarmupID(m.getId())) { return; }
//
//        if (dropped) {
//            this.failedDeliveries++;
//            if (this.failedRoutes.size() < MAX_EXAMPLE_ROUTES) {
//                this.failedRoutes.add(formatRoute(m, "FAILED at " + where));
//
//            }
//
//            double reward = -1.0;
//            List<DTNHost> hops = m.getHops();
//            if (hops.size() < 2) return;
//
//            DTNHost decisionMaker = hops.get(hops.size() - 2);
//            if (decisionMaker.getRouter() instanceof QLearningRouter) {
//                QLearningRouter router = (QLearningRouter) decisionMaker.getRouter();
//                RLState state = new RLState(decisionMaker, where, m);
//                router.updateQValue(state, QLearningRouter.ACTION_FORWARD, reward);
//            }
//        }
//    }
//
//    private String formatRoute(Message m, String outcome) {
//        StringBuilder sb = new StringBuilder();
//        sb.append(m.getId()).append(": ");
//        List<DTNHost> hops = m.getHops();
//        for (int i = 0; i < hops.size(); i++) {
//            sb.append(hops.get(i).toString());
//            if (i < hops.size() - 1) {
//                sb.append(" -> ");
//            }
//        }
//        sb.append(" (").append(outcome).append(")");
//        return sb.toString();
//    }
//
//    @Override
//    public void done() {
//        // --- Bagian 1: Laporan Kinerja dengan Perhitungan yang Benar ---
//        write("==========================================================");
//        write("        Performance & Route Analysis Report");
//        write("        (Centralized Q-Learning Model - BASELINE)");
//        write("==========================================================");
//        write("Scenario: " + getScenarioName() + " | End Time: " + format(getSimTime()));
//        write("----------------------------------------------------------");
//
//        write("Total Messages Created: " + this.nrofCreated);
//        write("Total Messages Delivered Successfully: " + this.successfulDeliveries);
//        write("Total Messages Failed (Dropped): " + this.failedDeliveries);
//
//        // PERHITUNGAN AKURAT
//        double deliveryRatio = 0;
//        if (this.nrofCreated > 0) {
//            deliveryRatio = ((double)this.successfulDeliveries / this.nrofCreated) * 100;
//        }
//        write(String.format("Delivery Success Ratio: %.2f%%", deliveryRatio));
//
//        write("\n--- Example of Successful Routes ---");
//        if (successfulRoutes.isEmpty()) { write("No messages were delivered successfully."); }
//        else { successfulRoutes.forEach(this::write); }
//
//        write("\n--- Example of Failed Routes ---");
//        if (failedRoutes.isEmpty()) { write("No messages were recorded as failed."); }
//        else { failedRoutes.forEach(this::write); }
//
//        // --- Bagian 2: Laporan Pembelajaran (Hanya satu Q-Table global) ---
//        write("\n==========================================================");
//        write("         Final Q-Table (Shared by All Agents)");
//        write("==========================================================");
//
//        // Mengambil Q-Table static langsung dari kelas QLearningRouter
//        Map<RLState, Map<Integer, Double>> qTable = QLearningRouter.getQTable();
//
//        if (qTable.isEmpty()) {
//            write("The shared Q-Table is empty. No learning opportunities occurred.");
//        } else {
//            for (Map.Entry<RLState, Map<Integer, Double>> entry : qTable.entrySet()) {
//                RLState state = entry.getKey();
//                Map<Integer, Double> actions = entry.getValue();
//                Double qDoNotForward = actions.getOrDefault(QLearningRouter.ACTION_DO_NOT_FORWARD, 0.0);
//                Double qForward = actions.getOrDefault(QLearningRouter.ACTION_FORWARD, 0.0);
//                String decision = (qForward > qDoNotForward) ? "FORWARD" : "DO NOT FORWARD";
//                String output = String.format("IF %-30s | THEN best action is %-15s | (Q_Fwd: %7.4f, Q_NoFwd: %7.4f)",
//                        state.toString(), decision, qForward, qDoNotForward);
//                write(output);
//            }
//        }
//
//        write("\n==========================================================");
//        super.done();
//    }
//
//    // Metode lain dari MessageListener
//    @Override public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {}
//    @Override public void messageTransferAborted(Message m, DTNHost from, DTNHost to) {}
//
//    // Untuk kompatibilitas
//    public void messageDeleted(Message m, DTNHost where, int bytes, boolean dropped) {
//        this.messageDeleted(m, where, dropped);
//    }
//}


// File: RLRewardReporter.java (Untuk Baseline dengan Future Reward)
package report;

import core.*;
import routing.rl.QLearningRouter;
import routing.rl.RLState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reporter yang didesain untuk memicu pembelajaran berbasis episode
 * pada QLearningRouter dan melaporkan hasilnya.
 *
 * © 2025 hendrowunga, University of Sanata Dharma
 */
public class RLRewardReporter extends Report implements MessageListener {

    private int nrofCreated;
    private int successfulDeliveries;
    private int failedDeliveries;
    private List<String> successfulRoutes;
    private List<String> failedRoutes;
    private static final int MAX_EXAMPLE_ROUTES = 5;

    public RLRewardReporter() {
        super();
        init();
    }

    @Override
    public void init() {
        super.init();
        this.successfulRoutes = new ArrayList<>();
        this.failedRoutes = new ArrayList<>();
        this.nrofCreated = 0;
        this.successfulDeliveries = 0;
        this.failedDeliveries = 0;
    }

    @Override
    public void newMessage(Message m) {
        if (isWarmupID(m.getId())) {
            return;
        }
        this.nrofCreated++;
    }

    // --- PERUBAHAN UTAMA DI SINI ---
    @Override
    public void messageTransferred(Message m, DTNHost from, DTNHost to, boolean firstDelivery) {
        if (isWarmupID(m.getId())) { return; }

        if (firstDelivery) {
            this.successfulDeliveries++;
            if (this.successfulRoutes.size() < MAX_EXAMPLE_ROUTES) {
                this.successfulRoutes.add(formatRoute(m, "SUCCESS"));
            }

            // Panggil metode pembelajaran berbasis episode dengan reward positif
            double finalReward = 1.0;
            QLearningRouter.learnFromEpisode(m.getId(), finalReward);
        }
    }

    // --- DAN PERUBAHAN UTAMA DI SINI ---
    @Override
    public void messageDeleted(Message m, DTNHost where, boolean dropped) {
        if (isWarmupID(m.getId())) { return; }

        if (dropped) {
            this.failedDeliveries++;
            if (this.failedRoutes.size() < MAX_EXAMPLE_ROUTES) {
                this.failedRoutes.add(formatRoute(m, "FAILED at " + where));
            }

            // Panggil metode pembelajaran berbasis episode dengan reward negatif
            double finalReward = -1.0;
            QLearningRouter.learnFromEpisode(m.getId(), finalReward);
        }
    }

    private String formatRoute(Message m, String outcome) {
        StringBuilder sb = new StringBuilder();
        sb.append(m.getId()).append(": ");
        List<DTNHost> hops = m.getHops();
        for (int i = 0; i < hops.size(); i++) {
            sb.append(hops.get(i).toString());
            if (i < hops.size() - 1) {
                sb.append(" -> ");
            }
        }
        sb.append(" (").append(outcome).append(")");
        return sb.toString();
    }

    @Override
    public void done() {
        // --- Bagian 1: Laporan Kinerja (tidak berubah) ---
        write("==========================================================");
        write("        Performance & Route Analysis Report");
        write("        (Centralized Q-Learning Model with Future Reward)");
        write("==========================================================");
        write("Scenario: " + getScenarioName() + " | End Time: " + format(getSimTime()));
        write("----------------------------------------------------------");

        write("Total Messages Created: " + this.nrofCreated);
        write("Total Messages Delivered Successfully: " + this.successfulDeliveries);
        write("Total Messages Failed (Dropped): " + this.failedDeliveries);

        double deliveryRatio = 0;
        if (this.nrofCreated > 0) {
            deliveryRatio = ((double)this.successfulDeliveries / this.nrofCreated) * 100;
        }
        write(String.format("Delivery Success Ratio: %.2f%%", deliveryRatio));

        write("\n--- Example of Successful Routes ---");
        if (successfulRoutes.isEmpty()) { write("No messages were delivered successfully."); }
        else { successfulRoutes.forEach(this::write); }

        write("\n--- Example of Failed Routes ---");
        if (failedRoutes.isEmpty()) { write("No messages were recorded as failed."); }
        else { failedRoutes.forEach(this::write); }

        // --- Bagian 2: Laporan Pembelajaran (tidak berubah) ---
        write("\n==========================================================");
        write("         Final Q-Table (Shared by All Agents)");
        write("==========================================================");

        Map<RLState, Map<Integer, Double>> qTable = QLearningRouter.getQTable();

        if (qTable.isEmpty()) {
            write("The shared Q-Table is empty. No learning opportunities occurred.");
        } else {
            for (Map.Entry<RLState, Map<Integer, Double>> entry : qTable.entrySet()) {
                RLState state = entry.getKey();
                Map<Integer, Double> actions = entry.getValue();
                Double qDoNotForward = actions.getOrDefault(QLearningRouter.ACTION_DO_NOT_FORWARD, 0.0);
                Double qForward = actions.getOrDefault(QLearningRouter.ACTION_FORWARD, 0.0);
                String decision = (qForward > qDoNotForward) ? "FORWARD" : "DO NOT FORWARD";
                String output = String.format("IF %-30s | THEN best action is %-15s | (Q_Fwd: %7.4f, Q_NoFwd: %7.4f)",
                        state.toString(), decision, qForward, qDoNotForward);
                write(output);
            }
        }

        write("\n==========================================================");
        super.done();
    }

    // Metode lain dari MessageListener
    @Override public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {}
    @Override public void messageTransferAborted(Message m, DTNHost from, DTNHost to) {}

    // Untuk kompatibilitas
    public void messageDeleted(Message m, DTNHost where, int bytes, boolean dropped) {
        this.messageDeleted(m, where, dropped);
    }
}