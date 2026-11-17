package org.fog.research.experiments;

import org.fog.research.utils.Constants;

public class ExperimentConfig {
    public int numUavs = 4;
    public int numMds = 800;
    public double coverageRadius = 100.0;
    public double simulationDuration = 100.0; // 100 time slots (seconds)
    public String algorithm = "TF-PPO-MOGS"; // TF-PPO-MOGS, MAPPO, VDN, QMIX, MADDPG
    public double speed = Constants.UAV_MAX_SPEED;
    public double areaX = Constants.ENVIRONMENT_SIZE_X;
    public double areaY = Constants.ENVIRONMENT_SIZE_Y;
    public boolean randomizeUavs = false;
    public int placementProfile = 1; // 1 = Grid, 2 = Random, 3 = Custom
    public double[] customX = null;
    public double[] customY = null;
    public int episodes = 1;
}
