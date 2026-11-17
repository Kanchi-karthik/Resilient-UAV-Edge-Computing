package org.fog.research.computation;

import org.fog.research.environment.ResearchTask;
import org.fog.research.utils.TaskStatus;

public class TaskExecutor {

    public static boolean executeStep(ResearchTask task, double allocatedMips, double timeStepSec, double currentTime) {
        if (task.getResearchTaskStatus() != TaskStatus.EXECUTING) {
            task.setResearchTaskStatus(TaskStatus.EXECUTING);
        }
        
        // Eq. (17) constraint check: C_tau <= t_i + sigma
        if (task.isExpired(currentTime)) {
            task.setResearchTaskStatus(TaskStatus.FAILED);
            return false;
        }

        // Calculate progress made in this time step
        long completedCycles = (long) (allocatedMips * timeStepSec);
        long remainingCycles = task.getCloudletLength() - task.getCloudletFinishedSoFar();
        
        if (completedCycles >= remainingCycles) {
            task.setCloudletFinishedSoFar(task.getCloudletLength());
            task.setResearchTaskStatus(TaskStatus.COMPLETED);
            double compLatency = (double) task.getCloudletLength() / (allocatedMips * 1e6);
            task.setFinishTime(task.getGenerationTime() + task.getTxLatency() + compLatency);
            return true;
        } else {
            task.setCloudletFinishedSoFar(task.getCloudletFinishedSoFar() + completedCycles);
            
            // Check if it will expire by the next step if we continue at this rate
            if (task.isExpired(currentTime + timeStepSec)) {
                task.setResearchTaskStatus(TaskStatus.FAILED);
            }
            return false;
        }
    }
}
