package routing.carl_dtn.fuzzy;

import core.DTNHost;
import net.sourceforge.jFuzzyLogic.FIS;
import net.sourceforge.jFuzzyLogic.FunctionBlock;
import routing.carl_dtn.ContextAwareRLRouter;

/**
 * Kelas ini mengevaluasi konteks sebuah pesan (TTL dan Hop Count)
 * untuk menghasilkan skor "Prioritas Pesan" sesuai FLC3 di paper.
 */
public class FuzzyMessageEvaluator {
    public double evaluateMessagePriority(ContextAwareRLRouter hostRouter, int msgTTL, int msgHopCount) {
        FIS fis = hostRouter.getFclMessageEvaluatorFIS();
        if (fis == null) return 0.0;
        FunctionBlock fb = fis.getFunctionBlock("FuzzyMessageContext");
        if (fb == null) return 0.0;

        // Normalisasi input
        final double MAX_TTL = hostRouter.msgTtl * 60.0; // msgTtl dari settings (dalam detik)
        final double MAX_HOP_COUNT = 20.0;

        double normTTL = Math.min(msgTTL / MAX_TTL, 1.0);
        double normHopCount = Math.min(msgHopCount / MAX_HOP_COUNT, 1.0);

        fb.setVariable("msgTTL", normTTL);
        fb.setVariable("msgHopCount", normHopCount);
        fb.evaluate();

        return fb.getVariable("MESSAGE_PRIORITY").getValue();
    }
}