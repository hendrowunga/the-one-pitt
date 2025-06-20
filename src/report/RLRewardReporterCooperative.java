package report;
import core.*;
import routing.rl.CooperativeQLearningRouter; // PENTING: Impor router KOOPERATIF
import routing.rl.RLState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
/*
 * © 2025 hendrowunga, University of Sanata Dharma
 * Created on 6/8/25
 */
public class RLRewardReporterCooperative extends Report implements MessageListener {

    private int nrofCreated;
    private int successfulDeliveries;
    private int failedDeliveries;
    private List<String> successfulRoutes;
    private List<String> failedRoutes;
    private static final int MAX_EXAMPLE_ROUTES = 5;

    public RLRewardReporterCooperative() {
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

//    @Override
//    public void messageTransferred(Message m, DTNHost from, DTNHost to, boolean firstDelivery) {
//        if (isWarmupID(m.getId())) { return; }
//
//        if (firstDelivery) {
//            this.successfulDeliveries++;
//            if (this.successfulRoutes.size() < MAX_EXAMPLE_ROUTES) {
//                this.successfulRoutes.add(formatRoute(m, "SUCCESS"));
//            }
//
//            double reward = 1.0;
//            // Secara spesifik memeriksa instance dari CooperativeQLearningRouter
//            if (from.getRouter() instanceof CooperativeQLearningRouter) {
//                CooperativeQLearningRouter router = (CooperativeQLearningRouter) from.getRouter();
//                RLState state = new RLState(from, to, m);
//                router.updateQValue(state, CooperativeQLearningRouter.ACTION_FORWARD, reward);
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
//            }
//
//            double reward = -1.0;
//            List<DTNHost> hops = m.getHops();
//            if (hops.size() < 2) return;
//
//            DTNHost decisionMaker = hops.get(hops.size() - 2);
//            if (decisionMaker.getRouter() instanceof CooperativeQLearningRouter) {
//                CooperativeQLearningRouter router = (CooperativeQLearningRouter) decisionMaker.getRouter();
//                RLState state = new RLState(decisionMaker, where, m);
//                router.updateQValue(state, CooperativeQLearningRouter.ACTION_FORWARD, reward);
//            }
//        }
//    }

    @Override
    public void messageTransferred(Message m, DTNHost from, DTNHost to, boolean firstDelivery) {
        if (isWarmupID(m.getId())) { return; }

        if (firstDelivery) {
            this.successfulDeliveries++;
            if (this.successfulRoutes.size() < MAX_EXAMPLE_ROUTES) {
                this.successfulRoutes.add(formatRoute(m, "SUCCESS"));
            }

            // PERBAIKAN: Panggil learnFromEpisode pada SEMUA node yang terlibat
            double finalReward = 1.0;
            List<DTNHost> hops = m.getHops();
            for (DTNHost hop : hops) {
                if (hop.getRouter() instanceof CooperativeQLearningRouter) {
                    ((CooperativeQLearningRouter) hop.getRouter()).learnFromEpisode(m.getId(), finalReward);
                }
            }
        }
    }

    @Override
    public void messageDeleted(Message m, DTNHost where, boolean dropped) {
        if (isWarmupID(m.getId())) { return; }

        if (dropped) {
            this.failedDeliveries++;
            if (this.failedRoutes.size() < MAX_EXAMPLE_ROUTES) {
                this.failedRoutes.add(formatRoute(m, "FAILED at " + where));
            }

            // PERBAIKAN: Panggil learnFromEpisode pada SEMUA node yang terlibat
            double finalReward = -1.0;
            List<DTNHost> hops = m.getHops();
            for (DTNHost hop : hops) {
                if (hop.getRouter() instanceof CooperativeQLearningRouter) {
                    ((CooperativeQLearningRouter) hop.getRouter()).learnFromEpisode(m.getId(), finalReward);
                }
            }
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
        // Bagian 1: Laporan Kinerja
        write("==========================================================");
        write("        Performance & Route Analysis Report");
        write("        (Cooperative Q-Learning Model)"); // Judul diubah
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

        // Bagian 2: Laporan Pembelajaran dari sampel agen
        write("\n==========================================================");
        write("         Final Q-Table (Samples from Multiple Agents)");
        write("==========================================================");

        List<DTNHost> allHosts = SimScenario.getInstance().getHosts();
        List<DTNHost> sampleAgents = new ArrayList<>();

        for (DTNHost host : allHosts) {
            if (host.getRouter() instanceof CooperativeQLearningRouter) {
                sampleAgents.add(host);
            }
        }

        Collections.shuffle(sampleAgents);
        int agentsToSample = 5;
        if (sampleAgents.size() < agentsToSample) {
            agentsToSample = sampleAgents.size();
        }

        if (agentsToSample == 0) {
            write("No agents with CooperativeQLearningRouter found.");
        } else {
            for (int i = 0; i < agentsToSample; i++) {
                DTNHost sampleHost = sampleAgents.get(i);
                CooperativeQLearningRouter router = (CooperativeQLearningRouter) sampleHost.getRouter();
                Map<RLState, Map<Integer, Double>> qTable = router.getQTable();

                write("\n--- Q-Table from Sample Agent: " + sampleHost.toString() + " ---");

                if (qTable.isEmpty()) {
                    write("This agent's Q-Table is empty.");
                } else {
                    for (Map.Entry<RLState, Map<Integer, Double>> entry : qTable.entrySet()) {
                        RLState state = entry.getKey();
                        Map<Integer, Double> actions = entry.getValue();
                        Double qDoNotForward = actions.getOrDefault(CooperativeQLearningRouter.ACTION_DO_NOT_FORWARD, 0.0);
                        Double qForward = actions.getOrDefault(CooperativeQLearningRouter.ACTION_FORWARD, 0.0);
                        String decision = (qForward > qDoNotForward) ? "FORWARD" : "DO NOT FORWARD";
                        String output = String.format("IF %-30s | THEN best action is %-15s | (Q_Fwd: %7.4f, Q_NoFwd: %7.4f)",
                                state.toString(), decision, qForward, qDoNotForward);
                        write(output);
                    }
                }
            }
        }

        write("\n==========================================================");
        super.done();
    }

    // Metode lain dari MessageListener
    @Override public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {}
    @Override public void messageTransferAborted(Message m, DTNHost from, DTNHost to) {}

    public void messageDeleted(Message m, DTNHost where, int bytes, boolean dropped) {
        this.messageDeleted(m, where, dropped);
    }
}