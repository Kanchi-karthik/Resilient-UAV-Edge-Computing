package org.fog.research.experiments;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.cloudbus.cloudsim.power.models.PowerModelLinear;
import org.fog.research.algorithms.mogs.ManyToOneGaleShapley;
import org.fog.research.algorithms.tfppo.TFPPO;
import org.fog.research.algorithms.TrajectoryPolicy;
import org.fog.research.algorithms.Scheduler;
import org.fog.research.communication.CommunicationModel;
import org.fog.research.computation.ComputationModel;
import org.fog.research.environment.MobileDevice;
import org.fog.research.environment.ResearchTask;
import org.fog.research.environment.UAVEntity;
import org.fog.research.metrics.MetricsCollector;
import org.fog.research.metrics.SimulationMetrics;
import org.fog.research.utils.Action;
import org.fog.research.utils.Constants;
import org.fog.research.utils.Vector2D;
import org.fog.research.utils.Vector3D;
import org.fog.utils.distribution.DeterministicDistribution;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.Config;
import org.fog.utils.FogUtils;
import java.util.LinkedList;

public class ExperimentRunner {
    private final Random rand = new Random(42);

    public SimulationMetrics runExperiment(ExperimentConfig config) {
        int observationDim = 4;
        List<Integer> mockUavIds = new ArrayList<>();
        for (int i = 0; i < config.numUavs; i++) {
            mockUavIds.add(i);
        }
        
        TrajectoryPolicy trajPolicy;
        if (config.algorithm.equals("TF-PPO-MOGS")) {
            trajPolicy = new org.fog.research.algorithms.TrajectoryPolicyImplementations.TFPPOPolicy(
                new org.fog.research.algorithms.tfppo.TFPPO(mockUavIds, observationDim)
            );
        } else {
            trajPolicy = new org.fog.research.algorithms.TrajectoryPolicyImplementations.BaselineTrajectoryPolicy(config.algorithm);
        }
        
        Scheduler scheduler = new org.fog.research.algorithms.SchedulerImplementations.MOGSScheduler();
        
        return runExperiment(config, trajPolicy, scheduler);
    }

    public SimulationMetrics runExperiment(ExperimentConfig config, TrajectoryPolicy trajectoryPolicy, Scheduler scheduler) {
        if (trajectoryPolicy instanceof org.fog.research.algorithms.TrajectoryPolicyImplementations.TFPPOPolicy && config.episodes > 1) {
            org.fog.research.algorithms.tfppo.TFPPO tfPpoAgent = ((org.fog.research.algorithms.TrajectoryPolicyImplementations.TFPPOPolicy) trajectoryPolicy).getTfPpo();
            System.out.println("Starting pre-training phase for " + (config.episodes - 1) + " episodes...");
            for (int ep = 0; ep < config.episodes - 1; ep++) {
                runEpisode(config, trajectoryPolicy, scheduler, false);
            }
            System.out.println("Pre-training phase completed. Executing evaluation episode...");
        }
        return runEpisode(config, trajectoryPolicy, scheduler, true);
    }

    private SimulationMetrics runEpisode(ExperimentConfig config, TrajectoryPolicy trajectoryPolicy, Scheduler scheduler, boolean isEvaluation) {
        List<UAVEntity> uavs = new ArrayList<>();
        List<Integer> uavIds = new ArrayList<>();
        PowerModelLinear pm = new PowerModelLinear(100, 10);
        Map<Integer, Double> uavDistances = new HashMap<>();
        Map<Integer, Integer> uavCoverageTime = new HashMap<>();
        
        try {
            Random initRand = new Random(100);
            for (int i = 0; i < config.numUavs; i++) {
                double initX, initY, radius;
                radius = config.coverageRadius;
                
                if (config.placementProfile == 2 || config.randomizeUavs) {
                    initX = initRand.nextDouble() * config.areaX;
                    initY = initRand.nextDouble() * config.areaY;
                    radius = 50.0 + initRand.nextDouble() * 100.0;
                } else if (config.placementProfile == 3 && config.customX != null && config.customY != null) {
                    if (i < config.customX.length) {
                        initX = config.customX[i];
                    } else {
                        initX = (i + 1) * (config.areaX / (config.numUavs + 1));
                    }
                    if (i < config.customY.length) {
                        initY = config.customY[i];
                    } else {
                        initY = config.areaY / 2;
                    }
                } else {
                    initX = (i + 1) * (config.areaX / (config.numUavs + 1));
                    initY = config.areaY / 2;
                }
                Vector3D pos = new Vector3D(initX, initY, Constants.UAV_ALTITUDE);

                List<Pe> peList = new ArrayList<>();
                peList.add(new Pe(0, new PeProvisionerOverbooking(1000)));

                int hostId = FogUtils.generateEntityId();
                long storage = 1000000;
                int bw = 10000;

                PowerHost host = new PowerHost(
                    hostId,
                    new RamProvisionerSimple(2048),
                    new BwProvisionerOverbooking(bw),
                    storage,
                    peList,
                    new StreamOperatorScheduler(peList),
                    pm
                );

                List<Host> hostList = new ArrayList<>();
                hostList.add(host);

                VmAllocationPolicy vmAllocationPolicy = new AppModuleAllocationPolicy(hostList);

                String arch = Config.FOG_DEVICE_ARCH;
                String os = Config.FOG_DEVICE_OS;
                String vmm = Config.FOG_DEVICE_VMM;
                double time_zone = Config.FOG_DEVICE_TIMEZONE;
                double cost = Config.FOG_DEVICE_COST;
                double costPerMem = Config.FOG_DEVICE_COST_PER_MEMORY;
                double costPerStorage = Config.FOG_DEVICE_COST_PER_STORAGE;
                double costPerBw = Config.FOG_DEVICE_COST_PER_BW;

                FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
                    arch, os, vmm, host, time_zone, cost, costPerMem,
                    costPerStorage, costPerBw
                );

                UAVEntity uav = new UAVEntity(
                    "UAV_" + i, 
                    characteristics, 
                    vmAllocationPolicy, 
                    new LinkedList<Storage>(), 
                    1.0, 
                    10000, 
                    10000, 
                    0.01, 
                    0.1, 
                    pos, 
                    radius
                );
                uavs.add(uav);
                uavIds.add(uav.getId());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        List<MobileDevice> mds = new ArrayList<>();
        Random devRand = new Random(200);
        for (int i = 0; i < config.numMds; i++) {
            Vector2D pos = new Vector2D(devRand.nextDouble() * config.areaX, devRand.nextDouble() * config.areaY);
            MobileDevice md = new MobileDevice("MD_" + i, "TaskType", i, "AppId", 
                                                new DeterministicDistribution(1.0), pos, 1.5, devRand.nextDouble() * 2 * Math.PI);
            mds.add(md);
        }

        CommunicationModel commModel = new CommunicationModel();
        ComputationModel compModel = new ComputationModel(Constants.SIMULATION_TIME_STEP_SEC);
        MetricsCollector collector = new MetricsCollector();

        int taskCounter = 0;
        double duration = isEvaluation ? config.simulationDuration : 100.0; // shorter training run

        double totalStepCoverage = 0.0;
        double totalStepCpuLoad = 0.0;
        int stepCount = 0;

        for (double t = 0; t < duration; t += Constants.SIMULATION_TIME_STEP_SEC) {
            
            // 1. Task Generation
            int tasksCompletedBefore = collector.getCompletedTasks().size();
            double energyBefore = getSwarmEnergyConsumed(uavs, config);

            List<ResearchTask> newTasks = new ArrayList<>();
            for (MobileDevice md : mds) {
                if (rand.nextDouble() < 0.05) {
                    long inputBits = (long) (1e5 + rand.nextDouble() * 9e5);
                    long outputBits = (long) (inputBits * 0.1);
                    long cpuCycles = (long) (10 + rand.nextDouble() * 90);
                    double latencyTolerance = 5.0 + rand.nextDouble() * 10.0;
                    
                    ResearchTask task = new ResearchTask("AppId", taskCounter++, md.getId(), 
                                                         inputBits, outputBits, cpuCycles, latencyTolerance, t);
                    md.addTaskToBuffer(task);
                    newTasks.add(task);
                    if (isEvaluation) {
                        collector.registerTask(task);
                    }
                }
            }

            // 2. UAV movement decisions
            Map<Integer, double[]> observations = new HashMap<>();
            for (UAVEntity uav : uavs) {
                double[] obs = new double[]{uav.getPosition().getX(), uav.getPosition().getY(), uav.getRemainingEnergy(), uav.getTaskQueue().size()};
                observations.put(uav.getId(), obs);
            }

            Map<Integer, Action> jointActions = trajectoryPolicy.selectActions(uavs, mds, t, config.areaX, config.areaY);

            for (UAVEntity uav : uavs) {
                if (uav.getRemainingEnergy() <= 0.0) {
                    continue;
                }
                Action act = jointActions.get(uav.getId());
                if (act != null) {
                    double speed = act.getDistance();
                    Vector3D nextPos = uav.getPosition().move(act.getDirectionRad(), speed, 0);
                    
                    double boundedX = Math.max(0.0, Math.min(config.areaX, nextPos.getX()));
                    double boundedY = Math.max(0.0, Math.min(config.areaY, nextPos.getY()));
                    uav.setPosition(new Vector3D(boundedX, boundedY, Constants.UAV_ALTITUDE));
                    
                    uav.consumeEnergy(speed * Constants.UAV_PROPEL_POWER_WATT / Constants.UAV_MAX_SPEED);
                    uavDistances.put(uav.getId(), uavDistances.getOrDefault(uav.getId(), 0.0) + speed);
                }
            }

            // 3. Ground devices mobility updates
            for (MobileDevice md : mds) {
                md.updatePosition(Constants.SIMULATION_TIME_STEP_SEC, 0, config.areaX, 0, config.areaY);
            }

            // 4. Transmission Rates updates
            Map<Integer, Map<Integer, Double>> uploadRates = new HashMap<>();
            Map<Integer, Map<Integer, Double>> downloadRates = new HashMap<>();
            
            for (MobileDevice md : mds) {
                Map<Integer, Double> up = new HashMap<>();
                Map<Integer, Double> down = new HashMap<>();
                for (UAVEntity uav : uavs) {
                    double distance = uav.getPosition().distanceTo2D(md.getPosition().getX(), md.getPosition().getY());
                    double snr = (Constants.XI * Constants.UAV_TRANSMISSION_POWER_WATT) / Math.max(distance * distance, 1.0);
                    double upRate = Constants.BANDWIDTH_HZ * (Math.log(1.0 + snr) / Math.log(2.0));
                    
                    double pLos = 1.0 / (1.0 + Constants.SIGMA * Math.exp(-Constants.LAMBDA * (90.0 - Constants.SIGMA)));
                    double pathLoss = pLos * (1.0 / Math.max(distance, 1.0));
                    double snrDown = (Constants.UAV_TRANSMISSION_POWER_WATT * pathLoss) / (Constants.BANDWIDTH_HZ * Constants.THERMAL_NOISE_WATT);
                    double downRate = Constants.BANDWIDTH_HZ * (Math.log(1.0 + snrDown) / Math.log(2.0));
                    
                    up.put(uav.getId(), upRate);
                    down.put(uav.getId(), downRate);
                }
                uploadRates.put(md.getId(), up);
                downloadRates.put(md.getId(), down);
            }

            // 5. Association Scheduling matching
            List<ResearchTask> pendingTasks = new ArrayList<>();
            for (MobileDevice md : mds) {
                pendingTasks.addAll(md.getTaskBuffer());
            }

            Map<Integer, Integer> assignments = scheduler.schedule(pendingTasks, uavs, uploadRates, downloadRates, t);

            for (ResearchTask task : pendingTasks) {
                Integer uavId = assignments.get(task.getTaskId());
                if (uavId != null) {
                    double upRate = uploadRates.get(task.getMobileDeviceId()).get(uavId);
                    double txLatency = (double) task.getInputBits() / upRate;
                    task.setTxLatency(txLatency);
                    double actualTxTime = Math.min(txLatency, Constants.SIMULATION_TIME_STEP_SEC);
                    UAVEntity uav = findUav(uavs, uavId);
                    if (uav != null && uav.getRemainingEnergy() > 0.0) {
                        uav.consumeEnergy(actualTxTime * Constants.UAV_TRANSMISSION_POWER_WATT);
                    }
                    
                    MobileDevice md = findMd(mds, task.getMobileDeviceId());
                    if (md != null) {
                        md.removeTaskFromBuffer(task);
                    }
                }
            }

            // 6. CPU Execution and energy update
            compModel.processBillingAndExecution(uavs, t);

            // 7. Track dynamic load/coverage metrics
            int coveredDevices = 0;
            for (MobileDevice md : mds) {
                boolean covered = false;
                for (UAVEntity uav : uavs) {
                    double dist = uav.getPosition().distanceTo2D(md.getPosition().getX(), md.getPosition().getY());
                    if (dist <= uav.getCoverageRadius()) {
                        covered = true;
                        break;
                    }
                }
                if (covered) coveredDevices++;
            }
            totalStepCoverage += (double) coveredDevices / mds.size();

            for (UAVEntity uav : uavs) {
                boolean coversAny = false;
                for (MobileDevice md : mds) {
                    double dist = uav.getPosition().distanceTo2D(md.getPosition().getX(), md.getPosition().getY());
                    if (dist <= uav.getCoverageRadius()) {
                        coversAny = true;
                        break;
                    }
                }
                if (coversAny) {
                    uavCoverageTime.put(uav.getId(), uavCoverageTime.getOrDefault(uav.getId(), 0) + 1);
                }
            }

            int executingCount = 0;
            for (UAVEntity uav : uavs) {
                executingCount += uav.getTaskQueue().size();
            }
            totalStepCpuLoad += (double) executingCount / uavs.size();
            stepCount++;

            // 8. PPO Step update with normalized reward scaling
            if (trajectoryPolicy instanceof org.fog.research.algorithms.TrajectoryPolicyImplementations.TFPPOPolicy) {
                org.fog.research.algorithms.tfppo.TFPPO tfPpoAgent = ((org.fog.research.algorithms.TrajectoryPolicyImplementations.TFPPOPolicy) trajectoryPolicy).getTfPpo();
                int completedInStep = collector.getCompletedTasks().size() - tasksCompletedBefore;
                double energyConsumedInStep = getSwarmEnergyConsumed(uavs, config) - energyBefore;
                int queuedTasks = getQueuedTasksCount(uavs);
                
                // Reward formulation: positive for throughput, negative for energy & queue delays
                double stepReward = (completedInStep * 1.5) - (energyConsumedInStep * 0.001) - (queuedTasks * 0.05);

                Map<Integer, double[]> nextObs = new HashMap<>();
                for (UAVEntity uav : uavs) {
                    double[] obs = new double[]{uav.getPosition().getX(), uav.getPosition().getY(), uav.getRemainingEnergy(), uav.getTaskQueue().size()};
                    nextObs.put(uav.getId(), obs);
                }
                tfPpoAgent.storeTransitions(observations, jointActions, stepReward, nextObs, false);
            }
        }

        // Episode End policy update
        if (trajectoryPolicy instanceof org.fog.research.algorithms.TrajectoryPolicyImplementations.TFPPOPolicy) {
            org.fog.research.algorithms.tfppo.TFPPO tfPpoAgent = ((org.fog.research.algorithms.TrajectoryPolicyImplementations.TFPPOPolicy) trajectoryPolicy).getTfPpo();
            Map<Integer, double[]> lastObs = new HashMap<>();
            for (UAVEntity uav : uavs) {
                double[] obs = new double[]{uav.getPosition().getX(), uav.getPosition().getY(), uav.getRemainingEnergy(), uav.getTaskQueue().size()};
                lastObs.put(uav.getId(), obs);
            }
            tfPpoAgent.updatePolicies(lastObs);
        }

        // Metrics output (Only for the final evaluation run)
        if (isEvaluation) {
            int failedCount = 0;
            int queuedCount = 0;
            int executingCount = 0;
            for (ResearchTask t : collector.getAllTasks()) {
                if (t.getResearchTaskStatus() == org.fog.research.utils.TaskStatus.FAILED) failedCount++;
                else if (t.getResearchTaskStatus() == org.fog.research.utils.TaskStatus.QUEUED) queuedCount++;
                else if (t.getResearchTaskStatus() == org.fog.research.utils.TaskStatus.EXECUTING) executingCount++;
            }
            
            double totalInitialEnergy = config.numUavs * Constants.UAV_BATTERY_CAPACITY_JOULES;
            double totalRemainingEnergy = 0.0;
            for (UAVEntity uav : uavs) {
                totalRemainingEnergy += uav.getRemainingEnergy();
            }
            double totalEnergyConsumed = Math.max(0.0, totalInitialEnergy - totalRemainingEnergy);
            double avgBatteryRemainingPct = (totalRemainingEnergy / totalInitialEnergy) * 100.0;
            
            double avgCoverage = stepCount > 0 ? totalStepCoverage / stepCount : 0.0;
            double avgCpuLoad = stepCount > 0 ? totalStepCpuLoad / stepCount : 0.0;

            SimulationMetrics metrics = collector.collect(duration, totalEnergyConsumed, avgBatteryRemainingPct, failedCount, queuedCount, executingCount, uavs, avgCoverage, avgCpuLoad);
            metrics.setRawUavs(new ArrayList<>(uavs));
            metrics.setRawTasks(new ArrayList<>(collector.getAllTasks()));
            metrics.setUavDistances(new HashMap<>(uavDistances));
            metrics.setUavCoverageTime(new HashMap<>(uavCoverageTime));
            return metrics;
        }

        return null;
    }

    private double getSwarmEnergyConsumed(List<UAVEntity> uavs, ExperimentConfig config) {
        double consumed = 0.0;
        for (UAVEntity uav : uavs) {
            consumed += (Constants.UAV_BATTERY_CAPACITY_JOULES - uav.getRemainingEnergy());
        }
        return consumed;
    }

    private int getQueuedTasksCount(List<UAVEntity> uavs) {
        int count = 0;
        for (UAVEntity uav : uavs) {
            count += uav.getTaskQueue().size();
        }
        return count;
    }

    private UAVEntity findUav(List<UAVEntity> uavs, int id) {
        for (UAVEntity uav : uavs) {
            if (uav.getId() == id) return uav;
        }
        return null;
    }

    private MobileDevice findMd(List<MobileDevice> mds, int id) {
        for (MobileDevice md : mds) {
            if (md.getId() == id) return md;
        }
        return null;
    }
}
