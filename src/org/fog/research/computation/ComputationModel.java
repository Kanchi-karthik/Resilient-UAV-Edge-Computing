package org.fog.research.computation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.fog.research.environment.ResearchTask;
import org.fog.research.environment.UAVEntity;
import org.fog.research.utils.TaskStatus;

public class ComputationModel {
    private final double timeStepSec;

    public ComputationModel(double timeStepSec) {
        this.timeStepSec = timeStepSec;
    }

    public void processBillingAndExecution(List<UAVEntity> uavs, double currentTime) {
        for (UAVEntity uav : uavs) {
            if (uav.getRemainingEnergy() <= 0.0) {
                continue; // UAV is out of battery: stop executing tasks
            }
            List<ResearchTask> activeTasks = new ArrayList<>();
            for (ResearchTask task : uav.getTaskQueue()) {
                if (task.getResearchTaskStatus() == TaskStatus.EXECUTING || 
                    task.getResearchTaskStatus() == TaskStatus.QUEUED) {
                    activeTasks.add(task);
                }
            }

            Map<Integer, Double> allocations = CPUAllocator.allocateCPU(uav, activeTasks);

            for (ResearchTask task : activeTasks) {
                double allocatedMips = allocations.getOrDefault(task.getTaskId(), 0.0);
                
                if (allocatedMips > 0 && uav.getRemainingEnergy() > 0.0) {
                    boolean completed = TaskExecutor.executeStep(task, allocatedMips, timeStepSec, currentTime);
                    
                    double energyConsumed = EnergyCalculator.calculateComputationEnergy(timeStepSec);
                    uav.consumeEnergy(energyConsumed);

                    if (completed) {
                        uav.removeTaskFromQueue(task);
                    } else if (task.getResearchTaskStatus() == TaskStatus.FAILED) {
                        uav.removeTaskFromQueue(task);
                    }
                }
            }
        }
    }
}
