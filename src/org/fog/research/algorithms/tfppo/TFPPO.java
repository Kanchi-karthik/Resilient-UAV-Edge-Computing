package org.fog.research.algorithms.tfppo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.fog.research.algorithms.ppo.PPO;
import org.fog.research.algorithms.factorization.TaskFactorizationNetwork;
import org.fog.research.utils.Action;

public class TFPPO {
    private final Map<Integer, PPO> agents;
    private final TaskFactorizationNetwork tfn;
    private final int numUavs;
    private final int observationDim;

    public TFPPO(List<Integer> uavIds, int observationDim) {
        this.numUavs = uavIds.size();
        this.observationDim = observationDim;
        this.agents = new HashMap<>();
        for (int id : uavIds) {
            agents.put(id, new PPO(observationDim));
        }
        this.tfn = new TaskFactorizationNetwork(numUavs);
    }

    public Map<Integer, Action> selectJointAction(Map<Integer, double[]> observations) {
        Map<Integer, Action> jointAction = new HashMap<>();
        for (Map.Entry<Integer, double[]> entry : observations.entrySet()) {
            int uavId = entry.getKey();
            double[] obs = entry.getValue();
            PPO agent = agents.get(uavId);
            if (agent != null) {
                jointAction.put(uavId, agent.selectAction(obs));
            }
        }
        return jointAction;
    }

    public void storeTransitions(Map<Integer, double[]> states, Map<Integer, Action> actions, 
                                   double globalReward, Map<Integer, double[]> nextStates, boolean done) {
        // Distribute the global reward among all coordinating agents
        double rewardShare = globalReward / numUavs;
        
        for (int uavId : agents.keySet()) {
            PPO agent = agents.get(uavId);
            double[] state = states.get(uavId);
            Action act = actions.get(uavId);
            double[] nextState = nextStates.get(uavId);
            
            if (agent != null && state != null && act != null && nextState != null) {
                double[] actArr = new double[]{act.getDirectionRad(), act.getDistance()};
                double logProb = agent.getLogProbability(act, state);
                agent.storeTransition(state, actArr, rewardShare, nextState, logProb, done);
            }
        }
    }

    public void updatePolicies(Map<Integer, double[]> lastObservations) {
        for (Map.Entry<Integer, PPO> entry : agents.entrySet()) {
            int uavId = entry.getKey();
            PPO agent = entry.getValue();
            double[] lastObs = lastObservations.get(uavId);
            if (agent != null && lastObs != null) {
                double lastVal = agent.evaluateState(lastObs);
                agent.updatePolicy(lastVal);
            }
        }
    }

    public Map<Integer, PPO> getAgents() {
        return agents;
    }

    public TaskFactorizationNetwork getTfn() {
        return tfn;
    }
}
