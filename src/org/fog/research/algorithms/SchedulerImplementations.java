package org.fog.research.algorithms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.fog.research.algorithms.mogs.ManyToOneGaleShapley;
import org.fog.research.environment.ResearchTask;
import org.fog.research.environment.UAVEntity;

public class SchedulerImplementations {

    // 1. MOGS Scheduler
    public static class MOGSScheduler implements Scheduler {
        @Override
        public Map<Integer, Integer> schedule(List<ResearchTask> tasks, List<UAVEntity> uavs,
                                              Map<Integer, Map<Integer, Double>> uploadRates,
                                              Map<Integer, Map<Integer, Double>> downloadRates,
                                              double currentTime) {
            ManyToOneGaleShapley mogs = new ManyToOneGaleShapley(uavs);
            return mogs.schedule(tasks, uploadRates, downloadRates, currentTime);
        }
    }

    // 2. Greedy Scheduler (Channel Rate based)
    public static class GreedyScheduler implements Scheduler {
        @Override
        public Map<Integer, Integer> schedule(List<ResearchTask> tasks, List<UAVEntity> uavs,
                                              Map<Integer, Map<Integer, Double>> uploadRates,
                                              Map<Integer, Map<Integer, Double>> downloadRates,
                                              double currentTime) {
            Map<Integer, Integer> assignments = new HashMap<>();
            for (ResearchTask task : tasks) {
                Map<Integer, Double> rates = uploadRates.get(task.getMobileDeviceId());
                int bestUavId = -1;
                double maxRate = -1.0;
                for (UAVEntity uav : uavs) {
                    double rate = (rates != null) ? rates.getOrDefault(uav.getId(), 0.0) : 0.0;
                    if (rate > maxRate) {
                        maxRate = rate;
                        bestUavId = uav.getId();
                    }
                }
                if (bestUavId != -1) {
                    assignments.put(task.getTaskId(), bestUavId);
                    task.setAssignedUavId(bestUavId);
                    UAVEntity uav = findUav(uavs, bestUavId);
                    if (uav != null) uav.addTaskToQueue(task);
                }
            }
            return assignments;
        }
    }

    // 3. Random Scheduler
    public static class RandomScheduler implements Scheduler {
        private final Random rand = new Random(42);

        @Override
        public Map<Integer, Integer> schedule(List<ResearchTask> tasks, List<UAVEntity> uavs,
                                              Map<Integer, Map<Integer, Double>> uploadRates,
                                              Map<Integer, Map<Integer, Double>> downloadRates,
                                              double currentTime) {
            Map<Integer, Integer> assignments = new HashMap<>();
            if (uavs.isEmpty()) return assignments;
            for (ResearchTask task : tasks) {
                UAVEntity uav = uavs.get(rand.nextInt(uavs.size()));
                assignments.put(task.getTaskId(), uav.getId());
                task.setAssignedUavId(uav.getId());
                uav.addTaskToQueue(task);
            }
            return assignments;
        }
    }

    // 4. Round Robin Scheduler
    public static class RoundRobinScheduler implements Scheduler {
        private int counter = 0;

        @Override
        public Map<Integer, Integer> schedule(List<ResearchTask> tasks, List<UAVEntity> uavs,
                                              Map<Integer, Map<Integer, Double>> uploadRates,
                                              Map<Integer, Map<Integer, Double>> downloadRates,
                                              double currentTime) {
            Map<Integer, Integer> assignments = new HashMap<>();
            if (uavs.isEmpty()) return assignments;
            for (ResearchTask task : tasks) {
                UAVEntity uav = uavs.get(counter % uavs.size());
                counter++;
                assignments.put(task.getTaskId(), uav.getId());
                task.setAssignedUavId(uav.getId());
                uav.addTaskToQueue(task);
            }
            return assignments;
        }
    }

    // 5. FCFS / Min Queue Size Scheduler
    public static class FCFSScheduler implements Scheduler {
        @Override
        public Map<Integer, Integer> schedule(List<ResearchTask> tasks, List<UAVEntity> uavs,
                                              Map<Integer, Map<Integer, Double>> uploadRates,
                                              Map<Integer, Map<Integer, Double>> downloadRates,
                                              double currentTime) {
            Map<Integer, Integer> assignments = new HashMap<>();
            for (ResearchTask task : tasks) {
                UAVEntity bestUav = null;
                int minQueue = Integer.MAX_VALUE;
                for (UAVEntity uav : uavs) {
                    if (uav.getTaskQueue().size() < minQueue) {
                        minQueue = uav.getTaskQueue().size();
                        bestUav = uav;
                    }
                }
                if (bestUav != null) {
                    assignments.put(task.getTaskId(), bestUav.getId());
                    task.setAssignedUavId(bestUav.getId());
                    bestUav.addTaskToQueue(task);
                }
            }
            return assignments;
        }
    }

    // 6. Genetic Algorithm (GA) Scheduler (heuristic representation)
    public static class GAScheduler implements Scheduler {
        private final Random rand = new Random();

        @Override
        public Map<Integer, Integer> schedule(List<ResearchTask> tasks, List<UAVEntity> uavs,
                                              Map<Integer, Map<Integer, Double>> uploadRates,
                                              Map<Integer, Map<Integer, Double>> downloadRates,
                                              double currentTime) {
            Map<Integer, Integer> assignments = new HashMap<>();
            if (uavs.isEmpty()) return assignments;
            
            for (ResearchTask task : tasks) {
                UAVEntity bestUav = null;
                double bestFitness = -1.0;
                for (UAVEntity uav : uavs) {
                    double rate = uploadRates.getOrDefault(task.getMobileDeviceId(), new HashMap<>()).getOrDefault(uav.getId(), 0.0);
                    double load = uav.getTaskQueue().size();
                    double fitness = rate / (1.0 + load * 0.5) + rand.nextDouble() * 1e5;
                    if (fitness > bestFitness) {
                        bestFitness = fitness;
                        bestUav = uav;
                    }
                }
                if (bestUav != null) {
                    assignments.put(task.getTaskId(), bestUav.getId());
                    task.setAssignedUavId(bestUav.getId());
                    bestUav.addTaskToQueue(task);
                }
            }
            return assignments;
        }
    }

    // 7. Particle Swarm Optimization (PSO) Scheduler (heuristic representation)
    public static class PSOScheduler implements Scheduler {
        @Override
        public Map<Integer, Integer> schedule(List<ResearchTask> tasks, List<UAVEntity> uavs,
                                              Map<Integer, Map<Integer, Double>> uploadRates,
                                              Map<Integer, Map<Integer, Double>> downloadRates,
                                              double currentTime) {
            Map<Integer, Integer> assignments = new HashMap<>();
            if (uavs.isEmpty()) return assignments;
            
            for (ResearchTask task : tasks) {
                UAVEntity bestUav = null;
                double minCost = Double.MAX_VALUE;
                for (UAVEntity uav : uavs) {
                    double rate = uploadRates.getOrDefault(task.getMobileDeviceId(), new HashMap<>()).getOrDefault(uav.getId(), 0.0);
                    double load = uav.getTaskQueue().size();
                    double cost = (1.0 / Math.max(rate, 1.0)) + (load * 0.2);
                    if (cost < minCost) {
                        minCost = cost;
                        bestUav = uav;
                    }
                }
                if (bestUav != null) {
                    assignments.put(task.getTaskId(), bestUav.getId());
                    task.setAssignedUavId(bestUav.getId());
                    bestUav.addTaskToQueue(task);
                }
            }
            return assignments;
        }
    }

    private static UAVEntity findUav(List<UAVEntity> uavs, int uavId) {
        for (UAVEntity uav : uavs) {
            if (uav.getId() == uavId) return uav;
        }
        return null;
    }
}
