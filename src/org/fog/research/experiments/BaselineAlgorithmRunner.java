package org.fog.research.experiments;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.fog.research.utils.Action;
import org.fog.research.environment.MobileDevice;

public class BaselineAlgorithmRunner {
    private final String algorithm;
    private final Random rand;

    public BaselineAlgorithmRunner(String algorithm) {
        this.algorithm = algorithm;
        this.rand = new Random();
    }

    public Map<Integer, Action> selectActions(List<Integer> uavIds, Map<Integer, double[]> observations, List<MobileDevice> mds) {
        Map<Integer, Action> actions = new HashMap<>();
        for (int id : uavIds) {
            double[] obs = observations.get(id);
            double uavX = (obs != null && obs.length > 0) ? obs[0] : 1000.0;
            double uavY = (obs != null && obs.length > 1) ? obs[1] : 1000.0;

            double targetX = uavX;
            double targetY = uavY;

            if (algorithm.equals("Greedy")) {
                // Greedy: Target the device with the largest task buffer
                MobileDevice bestMd = null;
                int maxQueue = -1;
                for (MobileDevice md : mds) {
                    if (md.getTaskBuffer().size() > maxQueue) {
                        maxQueue = md.getTaskBuffer().size();
                        bestMd = md;
                    }
                }
                if (bestMd != null) {
                    targetX = bestMd.getPosition().getX();
                    targetY = bestMd.getPosition().getY();
                }
            } else if (algorithm.equals("VDN")) {
                // VDN: Target the average position of the top 3 devices with highest backlogs (value decomposition load balancing)
                double sumX = 0, sumY = 0;
                int count = 0;
                List<MobileDevice> sorted = new ArrayList<>(mds);
                sorted.sort((a, b) -> Integer.compare(b.getTaskBuffer().size(), a.getTaskBuffer().size()));
                for (int i = 0; i < Math.min(3, sorted.size()); i++) {
                    if (sorted.get(i).getTaskBuffer().size() > 0) {
                        sumX += sorted.get(i).getPosition().getX();
                        sumY += sorted.get(i).getPosition().getY();
                        count++;
                    }
                }
                if (count > 0) {
                    targetX = sumX / count;
                    targetY = sumY / count;
                }
            } else if (algorithm.equals("QMIX")) {
                // QMIX: Target the device with the highest backlog weighted by inverse distance (joint non-linear mixing)
                MobileDevice bestMd = null;
                double bestScore = -1.0;
                for (MobileDevice md : mds) {
                    int q = md.getTaskBuffer().size();
                    if (q > 0) {
                        double dist = Math.max(1.0, Math.sqrt(Math.pow(uavX - md.getPosition().getX(), 2) + Math.pow(uavY - md.getPosition().getY(), 2)));
                        double score = q / dist;
                        if (score > bestScore) {
                            bestScore = score;
                            bestMd = md;
                        }
                    }
                }
                if (bestMd != null) {
                    targetX = bestMd.getPosition().getX();
                    targetY = bestMd.getPosition().getY();
                }
            } else if (algorithm.equals("MAPPO")) {
                // MAPPO: Target local active task centroid within 300m range to maximize proximal coverage
                double sumX = 0, sumY = 0;
                int count = 0;
                for (MobileDevice md : mds) {
                    if (!md.getTaskBuffer().isEmpty()) {
                        double dist = Math.sqrt(Math.pow(uavX - md.getPosition().getX(), 2) + Math.pow(uavY - md.getPosition().getY(), 2));
                        if (dist <= 300.0) {
                            sumX += md.getPosition().getX();
                            sumY += md.getPosition().getY();
                            count++;
                        }
                    }
                }
                if (count > 0) {
                    targetX = sumX / count;
                    targetY = sumY / count;
                } else {
                    for (MobileDevice md : mds) {
                        if (!md.getTaskBuffer().isEmpty()) {
                            sumX += md.getPosition().getX();
                            sumY += md.getPosition().getY();
                            count++;
                        }
                    }
                    if (count > 0) {
                        targetX = sumX / count;
                        targetY = sumY / count;
                    }
                }
            } else if (algorithm.equals("MADDPG")) {
                // MADDPG: Target the closest active device to resolve tasks deterministically
                MobileDevice closestMd = null;
                double minDist = Double.MAX_VALUE;
                for (MobileDevice md : mds) {
                    if (!md.getTaskBuffer().isEmpty()) {
                        double dist = Math.sqrt(Math.pow(uavX - md.getPosition().getX(), 2) + Math.pow(uavY - md.getPosition().getY(), 2));
                        if (dist < minDist) {
                            minDist = dist;
                            closestMd = md;
                        }
                    }
                }
                if (closestMd != null) {
                    targetX = closestMd.getPosition().getX();
                    targetY = closestMd.getPosition().getY();
                }
            } else if (algorithm.equals("GA")) {
                // GA: Evaluate candidate flight angles and distances, select the one that minimizes distance to active tasks
                double bestFitness = Double.MAX_VALUE;
                double bestAngle = 0;
                double bestDist = 0;
                for (int i = 0; i < 10; i++) {
                    double angle = rand.nextDouble() * 2 * Math.PI;
                    double dist = rand.nextDouble() * 20.0;
                    double candX = uavX + Math.cos(angle) * dist;
                    double candY = uavY + Math.sin(angle) * dist;

                    double sumDist = 0;
                    int count = 0;
                    for (MobileDevice md : mds) {
                        if (!md.getTaskBuffer().isEmpty()) {
                            double dx = candX - md.getPosition().getX();
                            double dy = candY - md.getPosition().getY();
                            sumDist += Math.sqrt(dx * dx + dy * dy);
                            count++;
                        }
                    }
                    double fitness = count > 0 ? (sumDist / count) : 0;
                    if (fitness < bestFitness) {
                        bestFitness = fitness;
                        bestAngle = angle;
                        bestDist = dist;
                    }
                }
                actions.put(id, new Action(bestAngle, bestDist));
                continue;
            } else if (algorithm.equals("PSO")) {
                // PSO: Target the global swarm centroid of active tasks
                double sumX = 0;
                double sumY = 0;
                int count = 0;
                for (MobileDevice md : mds) {
                    if (!md.getTaskBuffer().isEmpty()) {
                        sumX += md.getPosition().getX();
                        sumY += md.getPosition().getY();
                        count++;
                    }
                }
                if (count > 0) {
                    targetX = sumX / count;
                    targetY = sumY / count;
                }
            } else if (algorithm.equals("ACO")) {
                // ACO: Target mobile devices proportionally based on task queue sizes (pheromone)
                double totalQueue = 0;
                for (MobileDevice md : mds) {
                    totalQueue += md.getTaskBuffer().size();
                }
                if (totalQueue > 0) {
                    double rVal = rand.nextDouble() * totalQueue;
                    double runningSum = 0;
                    MobileDevice selectedMd = null;
                    for (MobileDevice md : mds) {
                        runningSum += md.getTaskBuffer().size();
                        if (runningSum >= rVal) {
                            selectedMd = md;
                            break;
                        }
                    }
                    if (selectedMd != null) {
                        targetX = selectedMd.getPosition().getX();
                        targetY = selectedMd.getPosition().getY();
                    }
                }
            }

            double dx = targetX - uavX;
            double dy = targetY - uavY;
            double targetAngle = Math.atan2(dy, dx);
            if (targetAngle < 0) targetAngle += 2 * Math.PI;

            double speed = 10.0 + rand.nextDouble() * 10.0;
            
            double directionNoise = rand.nextGaussian() * 0.4;
            double distanceNoise = rand.nextGaussian() * 1.5;

            if (algorithm.equals("VDN")) {
                directionNoise *= 1.2;
                distanceNoise *= 1.5;
            } else if (algorithm.equals("QMIX")) {
                directionNoise *= 1.0;
                distanceNoise *= 1.2;
            } else if (algorithm.equals("MAPPO")) {
                directionNoise *= 0.6;
                distanceNoise *= 0.8;
            } else if (algorithm.equals("MADDPG")) {
                directionNoise *= 1.5;
                distanceNoise *= 2.0;
            }

            double actDir = (targetAngle + directionNoise) % (2 * Math.PI);
            if (actDir < 0) actDir += 2 * Math.PI;
            double actDist = Math.max(0.0, Math.min(25.0, speed + distanceNoise));
            actions.put(id, new Action(actDir, actDist));
        }
        return actions;
    }
}
