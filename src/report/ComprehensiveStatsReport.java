package report;

import core.DTNHost;
import core.Message;
import core.MessageListener;

import java.util.ArrayList;
import java.util.List;

/**
 * A comprehensive report that generates a single CSV line with key performance metrics
 * at the end of the simulation. This simplifies data analysis by aggregating
 * delivery ratio, overhead, and latency into one file.
 *
 * This version is corrected to work within The ONE's Report class limitations.
 */
public class ComprehensiveStatsReport extends Report implements MessageListener {

    // Static variable to ensure header is written only once across all autoruns
    private static boolean headerWritten = false;

    private List<Message> deliveredMessages;
    private int messagesCreated;
    private int relayedMessagesCount;

    /**
     * Constructor.
     */
    public ComprehensiveStatsReport() {
        init();
    }

    @Override
    protected void init() {
        super.init();
        this.deliveredMessages = new ArrayList<>();
        this.messagesCreated = 0;
        this.relayedMessagesCount = 0;
    }

    @Override
    public void newMessage(Message m) {
        this.messagesCreated++;
    }

    @Override
    public void messageTransferred(Message m, DTNHost from, DTNHost to, boolean firstDelivery) {
        if (firstDelivery && m.getTo() == to) {
            this.deliveredMessages.add(m);
        }

        if (m.getTo() != to) {
            this.relayedMessagesCount++;
        }
    }

    @Override
    public void messageDeleted(Message m, DTNHost where, boolean dropped) {
        // Not used for primary stats, but could be used for buffer time analysis if needed.
    }

    @Override
    public void messageTransferAborted(Message m, DTNHost from, DTNHost to) {
    }

    @Override
    public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {
    }

    @Override
    public void done() {
        // --- Write Header (only once) ---
        if (!headerWritten) {
            // We removed avgBufferTime as it's better to get it from BufferStatsReport.
            // We also removed config settings as they are best sourced from the filename during post-processing.
            write("deliveryRatio,overheadRatio,avgLatency,avgHopCount,created,delivered,relayed");
            headerWritten = true;
        }

        // --- Metric Calculations ---
        double deliveryRatio = (this.messagesCreated > 0) ?
                (double) this.deliveredMessages.size() / this.messagesCreated : 0;

        double overheadRatio = (this.deliveredMessages.size() > 0) ?
                (double) this.relayedMessagesCount / this.deliveredMessages.size() : 0;

        double totalLatency = 0;
        for (Message m : this.deliveredMessages) {
            totalLatency += (m.getReceiveTime() - m.getCreationTime());
        }
        double avgLatency = (this.deliveredMessages.size() > 0) ?
                totalLatency / this.deliveredMessages.size() : 0;

        int totalHops = 0;
        for (Message m : this.deliveredMessages) {
            totalHops += m.getHopCount();
        }
        double avgHopCount = (this.deliveredMessages.size() > 0) ?
                (double) totalHops / this.deliveredMessages.size() : 0;

        // --- Write Data Row ---
        // Format: value1,value2,value3...
        String dataRow = String.format(java.util.Locale.US,
                "%.6f,%.6f,%.2f,%.2f,%d,%d,%d",
                deliveryRatio,
                overheadRatio,
                avgLatency,
                avgHopCount,
                this.messagesCreated,
                this.deliveredMessages.size(),
                this.relayedMessagesCount
        );

        write(dataRow);

        super.done();
    }
}