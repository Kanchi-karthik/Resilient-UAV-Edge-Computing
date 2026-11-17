package org.fog.research.algorithms.mogs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.fog.research.environment.ResearchTask;
import org.fog.research.environment.UAVEntity;

public class MatchingEngine {
    private static final int MAX_UAV_CAPACITY = 20;

    public static Map<Integer, Integer> runStableMatching(
            List<ResearchTask> tasks, List<UAVEntity> uavs,
            Map<Integer, List<Integer>> taskPreferences, Map<Integer, List<Integer>> uavPreferences) {
        
        Map<Integer, Integer> assignments = new HashMap<>();
        Map<Integer, List<Integer>> uavMatches = new HashMap<>();
        for (UAVEntity uav : uavs) {
            uavMatches.put(uav.getId(), new ArrayList<>());
        }

        Map<Integer, Integer> proposalIndices = new HashMap<>();
        List<Integer> freeTasks = new ArrayList<>();
        for (ResearchTask t : tasks) {
            freeTasks.add(t.getTaskId());
            proposalIndices.put(t.getTaskId(), 0);
        }

        int maxIterations = 100;
        int iter = 0;
        
        while (!freeTasks.isEmpty() && iter < maxIterations) {
            iter++;
            int taskId = freeTasks.remove(0);
            List<Integer> prefList = taskPreferences.get(taskId);
            int prefIdx = proposalIndices.get(taskId);
            
            if (prefList == null || prefIdx >= prefList.size()) {
                continue;
            }

            int uavId = prefList.get(prefIdx);
            proposalIndices.put(taskId, prefIdx + 1);

            List<Integer> matchedTasks = uavMatches.get(uavId);
            matchedTasks.add(taskId);
            assignments.put(taskId, uavId);

            if (matchedTasks.size() > MAX_UAV_CAPACITY) {
                List<Integer> prefUavTasks = uavPreferences.get(uavId);
                
                int worstTaskId = -1;
                int worstRank = -1;
                for (int mId : matchedTasks) {
                    int rank = prefUavTasks.indexOf(mId);
                    if (rank > worstRank) {
                        worstRank = rank;
                        worstTaskId = mId;
                    }
                }

                if (worstTaskId != -1) {
                    matchedTasks.remove((Integer) worstTaskId);
                    assignments.remove(worstTaskId);
                    freeTasks.add(worstTaskId);
                }
            }
        }
        return assignments;
    }
}
