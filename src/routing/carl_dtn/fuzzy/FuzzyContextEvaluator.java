package routing.carl_dtn.fuzzy;

import core.DTNHost;
import net.sourceforge.jFuzzyLogic.FIS;
import net.sourceforge.jFuzzyLogic.FunctionBlock;
import routing.carl_dtn.ContextAwareRLRouter;

/**
 * Kelas ini bertanggung jawab untuk mengevaluasi konteks node neighbor
 * menggunakan Fuzzy Logic Controller (FLC) hirarkis seperti di Gambar 2 paper.
 * Ini menghasilkan skor "Transfer Opportunity".
 */
public class FuzzyContextEvaluator {

    public double evaluateTransferOpportunity(ContextAwareRLRouter hostRouter, DTNHost neighbor) {
        FIS fis = hostRouter.getFclContextEvaluatorFIS();
        if (fis == null) return 0.0;
        FunctionBlock fb = fis.getFunctionBlock("FuzzyContextAware");
        if (fb == null) return 0.0;

        ContextAwareRLRouter neighborRouter = (ContextAwareRLRouter) neighbor.getRouter();

        // --- 1. Ambil semua metrik input ---
        double bufferKb = neighbor.getRouter().getFreeBufferSize() / 1024.0;
        double energy = neighborRouter.initialEnergy; // Menggunakan initialEnergy sebagai proksi
        double popularity = neighborRouter.getPopularity().getPopularity(neighbor);
        double tieStrength = hostRouter.getTieStrength().getTieStrength(hostRouter.getHost(), neighbor);

        // Normalisasi input
        final double MAX_BUFFER_KB = 50 * 1024.0; // 50 MB
        final double MAX_ENERGY = 500;
        double normBuffer = Math.min(bufferKb / MAX_BUFFER_KB, 1.0);
        double normEnergy = Math.min(energy / MAX_ENERGY, 1.0);

        // --- 2. Set input awal untuk FLC ---
        fb.setVariable("bufferNeighbor", normBuffer);
        fb.setVariable("energyNeighbor", normEnergy);
        fb.setVariable("popularityNeighbor", popularity);
        fb.setVariable("tieStrengthNeighbor", tieStrength);

        // --- 3. Evaluasi untuk mendapatkan output perantara (ABILITY & SOCIAL) ---
        fb.evaluate();
        double abilityNode = fb.getVariable("ABILITY_NODE").getValue();
        double socialImportance = fb.getVariable("SOCIAL_IMPORTANCE").getValue();

        // --- 4. Set output perantara sebagai input untuk evaluasi final ---
        fb.setVariable("ABILITY", abilityNode);
        fb.setVariable("SOCIAL", socialImportance);

        // --- 5. Evaluasi lagi untuk mendapatkan Transfer Opportunity ---
        fb.evaluate();

        return fb.getVariable("TRANSFER_OPPORTUNITY").getValue();
    }
}