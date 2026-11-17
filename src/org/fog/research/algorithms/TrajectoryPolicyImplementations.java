package org.fog.research.algorithms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.fog.research.algorithms.tfppo.TFPPO;
import org.fog.research.environment.MobileDevice;
import org.fog.research.environment.UAVEntity;
import org.fog.research.experiments.BaselineAlgorithmRunner;
import org.fog.research.utils.Action;

public class TrajectoryPolicyImplementations {

    // 1. TF-PPO Policy
    public static class TFPPOPolicy implements TrajectoryPolicy {
        private final TFPPO tfPpo;

        public TFPPOPolicy(TFPPO tfPpo) {
            this.tfPpo = tfPpo;
        }

        public TFPPO getTfPpo() {
            return tfPpo;
        }

        @Override
        public Map<Integer, Action> selectActions(List<UAVEntity> uavs, List<MobileDevice> mds,
                                                 double currentTime, double areaX, double areaY) {
            Map<Integer, double[]> observations = new HashMap<>();
            for (UAVEntity uav : uavs) {
                double[] obs = new double[]{
                    uav.getPosition().getX(), 
                    uav.getPosition().getY(), 
                    uav.getRemainingEnergy(), 
                    uav.getTaskQueue().size()
                };
                observations.put(uav.getId(), obs);
            }
            return tfPpo.selectJointAction(observations);
        }
    }

    // 2. Wrapper policy for VDN, QMIX, MAPPO, MADDPG, GA, PSO, ACO, Greedy
    public static class BaselineTrajectoryPolicy implements TrajectoryPolicy {
        private final BaselineAlgorithmRunner runner;

        public BaselineTrajectoryPolicy(String algorithm) {
            this.runner = new BaselineAlgorithmRunner(algorithm);
        }

        @Override
        public Map<Integer, Action> selectActions(List<UAVEntity> uavs, List<MobileDevice> mds,
                                                 double currentTime, double areaX, double areaY) {
            Map<Integer, double[]> observations = new HashMap<>();
            List<Integer> uavIds = new ArrayList<>();
            for (UAVEntity uav : uavs) {
                double[] obs = new double[]{
                    uav.getPosition().getX(), 
                    uav.getPosition().getY(), 
                    uav.getRemainingEnergy(), 
                    uav.getTaskQueue().size()
                };
                observations.put(uav.getId(), obs);
                uavIds.add(uav.getId());
            }
            return runner.selectActions(uavIds, observations, mds);
        }
    }

    // 3. Random Trajectory Policy (Lower bound)
    public static class RandomTrajectoryPolicy implements TrajectoryPolicy {
        private final Random rand = new Random();

        @Override
        public Map<Integer, Action> selectActions(List<UAVEntity> uavs, List<MobileDevice> mds,
                                                 double currentTime, double areaX, double areaY) {
            Map<Integer, Action> actions = new HashMap<>();
            for (UAVEntity uav : uavs) {
                double angle = rand.nextDouble() * 2 * Math.PI;
                double speed = rand.nextDouble() * 20.0;
                actions.put(uav.getId(), new Action(angle, speed));
            }
            return actions;
        }
    }
}
