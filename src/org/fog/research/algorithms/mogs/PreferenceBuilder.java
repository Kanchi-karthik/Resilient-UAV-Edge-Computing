package org.fog.research.algorithms.mogs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.fog.research.environment.ResearchTask;
import org.fog.research.environment.UAVEntity;

public class PreferenceBuilder {
    private static final double ALPHA_R = 0.4;
    private static final double BETA_R = 0.4;
    private static final double GAMMA_L = 0.2;

    public static List<Integer> buildTaskPreferenceList(ResearchTask task, List<UAVEntity> uavs, 
                                                         Map<Integer, Double> uploadRates, Map<Integer, Double> downloadRates) {
        List<UAVScore> scores = new ArrayList<>();
        for (UAVEntity uav : uavs) {
            double upRate = uploadRates.getOrDefault(uav.getId(), 0.0);
            double downRate = downloadRates.getOrDefault(uav.getId(), 0.0);
            double load = uav.getTaskQueue().size();
            
            // Eq. (27): Score to rank UAVs for a task
            double score = ALPHA_R * upRate + BETA_R * downRate - GAMMA_L * load;
            scores.add(new UAVScore(uav.getId(), score));
        }
        
        // Sort descending by score
        Collections.sort(scores, (a, b) -> Double.compare(b.score, a.score));
        
        List<Integer> preferenceList = new ArrayList<>();
        for (UAVScore us : scores) {
            preferenceList.add(us.uavId);
        }
        return preferenceList;
    }

    public static List<Integer> buildUAVPreferenceList(UAVEntity uav, List<ResearchTask> tasks) {
        List<TaskScore> scores = new ArrayList<>();
        for (ResearchTask task : tasks) {
            // UAV prefers tasks that have smaller data and lower CPU cycles to maximize throughput
            double score = 1.0 / (double) (task.getInputBits() + task.getCpuCycles());
            scores.add(new TaskScore(task.getTaskId(), score));
        }
        
        Collections.sort(scores, (a, b) -> Double.compare(b.score, a.score));
        
        List<Integer> preferenceList = new ArrayList<>();
        for (TaskScore ts : scores) {
            preferenceList.add(ts.taskId);
        }
        return preferenceList;
    }

    private static class UAVScore {
        int uavId;
        double score;
        UAVScore(int uavId, double score) {
            this.uavId = uavId;
            this.score = score;
        }
    }

    private static class TaskScore {
        int taskId;
        double score;
        TaskScore(int taskId, double score) {
            this.taskId = taskId;
            this.score = score;
        }
    }
}
