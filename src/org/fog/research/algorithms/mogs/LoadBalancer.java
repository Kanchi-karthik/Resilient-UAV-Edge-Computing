package org.fog.research.algorithms.mogs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.fog.research.environment.ResearchTask;
import org.fog.research.environment.UAVEntity;

public class LoadBalancer {

    // Eq. (28): canReassign checks if target UAV has resources and task will meet its deadline
    public static boolean canReassign(ResearchTask task, UAVEntity targetUav, double uploadRate, double downloadRate, double currentTime) {
        double transmissionLatency = task.getInputBits() / Math.max(uploadRate, 1.0) 
                                    + task.getOutputBits() / Math.max(downloadRate, 1.0);
        
        double queueWorkloadMips = 0.0;
        for (ResearchTask t : targetUav.getTaskQueue()) {
            queueWorkloadMips += t.getCloudletLength() - t.getCloudletFinishedSoFar();
        }
        double targetTotalMips = targetUav.getHost().getTotalMips();
        double executionLatency = (queueWorkloadMips + task.getCloudletLength()) / Math.max(targetTotalMips, 1.0);

        double totalEstimatedLatency = transmissionLatency + executionLatency;
        double remainingTolerance = task.getLatencyToleranceSec() - (currentTime - task.getGenerationTime());
        
        return totalEstimatedLatency <= remainingTolerance;
    }

    public static void balanceLoad(List<UAVEntity> uavs, Map<Integer, Integer> assignments, 
                                   List<ResearchTask> allTasks, Map<Integer, Map<Integer, Double>> linkUploadRates, 
                                   Map<Integer, Map<Integer, Double>> linkDownloadRates, double currentTime) {
        if (uavs.isEmpty()) return;
        
        double avgLoad = 0;
        for (UAVEntity uav : uavs) {
            avgLoad += uav.getTaskQueue().size();
        }
        avgLoad /= uavs.size();

        for (UAVEntity uavHigh : uavs) {
            if (uavHigh.getTaskQueue().size() > avgLoad + 2) {
                for (UAVEntity uavLow : uavs) {
                    if (uavLow.getTaskQueue().size() < avgLoad - 1) {
                        for (int i = 0; i < uavHigh.getTaskQueue().size(); i++) {
                            ResearchTask task = uavHigh.getTaskQueue().get(i);
                            
                            Map<Integer, Double> upMap = linkUploadRates.getOrDefault(task.getMobileDeviceId(), new HashMap<>());
                            Map<Integer, Double> downMap = linkDownloadRates.getOrDefault(task.getMobileDeviceId(), new HashMap<>());
                            
                            double upRate = upMap.getOrDefault(uavLow.getId(), 0.0);
                            double downRate = downMap.getOrDefault(uavLow.getId(), 0.0);
                            
                            if (canReassign(task, uavLow, upRate, downRate, currentTime)) {
                                uavHigh.removeTaskFromQueue(task);
                                uavLow.addTaskToQueue(task);
                                assignments.put(task.getTaskId(), uavLow.getId());
                                task.setAssignedUavId(uavLow.getId());
                                break;
                            }
                        }
                    }
                }
            }
        }
    }
}
