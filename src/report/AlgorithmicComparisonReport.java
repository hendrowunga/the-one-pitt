package report;

import core.ConnectionListener;
import core.DTNHost;
import core.Message;
import core.MessageListener;

import java.util.*;

public class AlgorithmicComparisonReport extends Report implements MessageListener, ConnectionListener {
    public static final String NROF_CONTACT_INTERVAL = "perTotalContact";
    public static final int DEFAULT_CONTACT_COUNT = 100; // Reduced default interval
    private int lastRecord;
    private int interval;
    private int nrofContact;
    private int nrofCreated;
    private int nrofDelivered;

    // Tambahan
    private Map<Integer, Double> overheadRatioPerContact;
    private Map<Integer, Double> avgLatencyPerContact;
    private Map<Integer, Double> totalForwardsPerContact;
    private double totalLatency = 0;
    private int totalForwards = 0;
    private int totalStarted = 0;

    private Map<Integer, Double> nrofDeliver;

    // Constructor:
    public AlgorithmicComparisonReport() {
        init();
        if (getSettings().contains(NROF_CONTACT_INTERVAL)) {
            interval = getSettings().getInt(NROF_CONTACT_INTERVAL);
        } else {
            interval = DEFAULT_CONTACT_COUNT;
        }
//        System.out.println("MessagePercentaceDeliveryPerContactReport initialized with interval: " + interval); // Debug
    }
    @Override
    protected void init() {
        super.init();
        this.nrofDelivered = 0;
        this.lastRecord = 0;
        this.nrofContact = 0;
        this.nrofCreated = 0; // Initialize nrofCreated
        this.totalStarted = 0;
        this.totalLatency = 0;
        this.totalForwards = 0;
        this.nrofDeliver = new HashMap<>();
        this.overheadRatioPerContact = new HashMap<>();
        this.avgLatencyPerContact = new HashMap<>();
        this.totalForwardsPerContact = new HashMap<>();

//        System.out.println("Report initialized"); // Debug
    }


    @Override
    public void hostsConnected(DTNHost host1, DTNHost host2) {
        nrofContact++;
//        System.out.println("Contact #" + nrofContact + " between " + host1 + " and " + host2); // Debug

        if (nrofContact - lastRecord >= interval) {
            lastRecord = nrofContact;
            double deliveryPercentage;
            double overheadRatio;
            double avgLatency;
            double avgForwards;

            if (nrofCreated > 0) {
                deliveryPercentage = ((1.0 * this.nrofDelivered) / this.nrofCreated) * 100;
            } else {
                deliveryPercentage = 0.0; // Avoid division by zero
            }

            if (nrofDelivered > 0) {
                overheadRatio = (1.0 * totalStarted) / nrofDelivered;
                avgLatency = totalLatency / nrofDelivered;
            } else {
                overheadRatio = 0.0; // Avoid division by zero
                avgLatency = 0.0;
            }
            if (nrofCreated > 0) {
                avgForwards = (double) totalForwards / nrofCreated;
            } else {
                avgForwards = 0.0; // Avoid division by zero
            }

            nrofDeliver.put(lastRecord, deliveryPercentage);
            overheadRatioPerContact.put(lastRecord, overheadRatio);
            avgLatencyPerContact.put(lastRecord, avgLatency);
            totalForwardsPerContact.put(lastRecord, avgForwards);

//            System.out.println("Recording data at contact #" + lastRecord +
//                    ", Created: " + nrofCreated +
//                    ", Delivered: " + nrofDelivered +
//                    ", Percentage: " + deliveryPercentage); // Debug
        }
    }

    @Override
    public void hostsDisconnected(DTNHost host1, DTNHost host2) {

    }

    @Override
    public void newMessage(Message m) {
        this.nrofCreated++;
//        System.out.println("New message created: " + m.getId() + ", Total created: " + nrofCreated); // Debug
    }

    @Override
    public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {
        totalStarted++;
    }

    @Override
    public void messageDeleted(Message m, DTNHost where, boolean dropped) {

    }

    @Override
    public void messageTransferAborted(Message m, DTNHost from, DTNHost to) {

    }

    @Override
    public void messageTransferred(Message m, DTNHost from, DTNHost to, boolean firstDelivery) {
        if (firstDelivery) {
            this.nrofDelivered++;
            totalLatency += (m.getReceiveTime() - m.getCreationTime());
//            System.out.println("Message delivered: " + m.getId() +
//                    ", from: " + from +
//                    ", to: " + to +
//                    ", Total delivered: " + nrofDelivered); // Debug
        }
        totalForwards++;
    }
    @Override
    public void done() {
//        double simulationTime = SimClock.getTime(); // Get simulation time
//        String output = "SimulationTime\t" + SimClock.getTime() + "\n"; // Add simulation time
        String output = "Contact\tDeliveryPercentage\tOverheadRatio\tAvgLatency\tTotalForwards\n";

        List<Map.Entry<Integer, Double>> sortedEntries = new ArrayList<>(nrofDeliver.entrySet());
        Collections.sort(sortedEntries, Map.Entry.comparingByKey()); // Ascending
//        Collections.sort(sortedEntries, Map.Entry.<Integer, Double>comparingByKey().reversed()); // Descending

        for (Map.Entry<Integer, Double> entry : sortedEntries) {
            Integer key = entry.getKey();
            Double deliveryPercentage = entry.getValue();
            Double overheadRatio = overheadRatioPerContact.get(key);
            Double avgLatency = avgLatencyPerContact.get(key);
            Double avgForwards = totalForwardsPerContact.get(key);

            output += key + "\t" + deliveryPercentage + "\t" + overheadRatio + "\t" + avgLatency + "\t" + avgForwards + "\n";
        }
        write(output);
        super.done();
    }
}