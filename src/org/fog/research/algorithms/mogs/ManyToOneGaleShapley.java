package org.fog.research.algorithms.mogs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.fog.research.environment.ResearchTask;
import org.fog.research.environment.UAVEntity;

public class ManyToOneGaleShapley {
    private final List<UAVEntity> uavs;

    public ManyToOneGaleShapley(List<UAVEntity> uavs) {
        this.uavs = uavs;
    }

    public Map<Integer, Integer> schedule(List<ResearchTask> tasks, 
                                          Map<Integer, Map<Integer, Double>> uploadRates, 
                                          Map<Integer, Map<Integer, Double>> downloadRates,
                                          double currentTime) {
        
        // 1. Build preference matrices
        Map<Integer, List<Integer>> taskPreferences = new HashMap<>();
        for (ResearchTask task : tasks) {
            Map<Integer, Double> upRates = uploadRates.getOrDefault(task.getMobileDeviceId(), new HashMap<>());
            Map<Integer, Double> downRates = downloadRates.getOrDefault(task.getMobileDeviceId(), new HashMap<>());
            List<Integer> pref = PreferenceBuilder.buildTaskPreferenceList(task, uavs, upRates, downRates);
            taskPreferences.put(task.getTaskId(), pref);
        }

        Map<Integer, List<Integer>> uavPreferences = new HashMap<>();
        for (UAVEntity uav : uavs) {
            List<Integer> pref = PreferenceBuilder.buildUAVPreferenceList(uav, tasks);
            uavPreferences.put(uav.getId(), pref);
        }

        // 2. Run Gale-Shapley matching engine
        Map<Integer, Integer> assignments = MatchingEngine.runStableMatching(
                tasks, uavs, taskPreferences, uavPreferences);

        // Assign tasks to matched UAV queues
        for (ResearchTask task : tasks) {
            Integer uavId = assignments.get(task.getTaskId());
            if (uavId != null) {
                task.setAssignedUavId(uavId);
                UAVEntity uav = findUav(uavId);
                if (uav != null) {
                    uav.addTaskToQueue(task);
                }
            }
        }

        // 3. Perform load balancing adjustments
        LoadBalancer.balanceLoad(uavs, assignments, tasks, uploadRates, downloadRates, currentTime);

        return assignments;
    }

    private UAVEntity findUav(int uavId) {
        for (UAVEntity uav : uavs) {
            if (uav.getId() == uavId) {
                return uav;
            }
        }
        return null;
    }
}
