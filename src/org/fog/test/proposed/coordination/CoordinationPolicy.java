package org.fog.test.proposed.coordination;

import java.util.List;
import org.fog.test.proposed.DeadlockRecoverer;
import org.fog.test.proposed.DeadlockReport;
import org.fog.test.proposed.ProposedUAVState;

/** Strategy boundary: the simulation loop is shared; only coordination differs. */
public interface CoordinationPolicy {
    String getName();
    void beginSlot(int slot, double clock, List<ProposedUAVState> uavs);
    boolean shouldDetect(List<ProposedUAVState> uavs, int completedThisSlot);
    boolean recover(DeadlockReport report, DeadlockRecoverer recoverer, double clock);
    void endSlot();
}