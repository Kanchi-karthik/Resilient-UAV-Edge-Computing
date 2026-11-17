package org.fog.test.proposed.coordination.wu2025;

import java.util.Arrays;

/**
 * WuChromosome - 4-part gene chromosome encoding according to Wu et al. (Scientific Reports 2025).
 * 
 * Each gene j represents:
 *  - targetId[j]: Target ID / Dependency Group ID
 *  - taskType[j]: Task type (1: Reconnaissance C, 2: Rescue R, 3: Verification V)
 *  - pathAngle[j]: Flight arrival angle (ψ ∈ [0, 360])
 *  - uavId[j]: Assigned UAV ID
 */
public class WuChromosome {

    public int[] targetId;
    public int[] taskType;
    public double[] pathAngle;
    public int[] uavId;
    public double fitness = 0.0;
    public boolean deadlocked = false;

    public WuChromosome(int length) {
        targetId = new int[length];
        taskType = new int[length];
        pathAngle = new double[length];
        uavId = new int[length];
    }

    public WuChromosome clone() {
        WuChromosome copy = new WuChromosome(targetId.length);
        copy.targetId = Arrays.copyOf(targetId, targetId.length);
        copy.taskType = Arrays.copyOf(taskType, taskType.length);
        copy.pathAngle = Arrays.copyOf(pathAngle, pathAngle.length);
        copy.uavId = Arrays.copyOf(uavId, uavId.length);
        copy.fitness = fitness;
        copy.deadlocked = deadlocked;
        return copy;
    }
}
