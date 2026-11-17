package org.fog.research.algorithms.ppo;

import java.util.Random;
import org.fog.research.utils.Action;

public class PPO {
    private final ActorNetwork actor;
    private final CriticNetwork critic;
    private final ExperienceBuffer buffer;
    private final int inputDim;
    private final Random rand;

    public PPO(int inputDim) {
        this.inputDim = inputDim;
        this.actor = new ActorNetwork(inputDim, 64, 2); // Outputs: mean direction (radians), mean distance
        this.critic = new CriticNetwork(inputDim, 64);
        this.buffer = new ExperienceBuffer();
        this.rand = new Random();
    }

    public Action selectAction(double[] observation) {
        double[] output = actor.forward(observation);
        double meanDir = output[0];
        double meanDist = output[1];

        // Sample action using exploration noise
        double std = 0.5;
        double actionDir = meanDir + rand.nextGaussian() * std;
        double actionDist = meanDist + rand.nextGaussian() * std;

        // Clip direction to [0, 2*PI] and distance to positive range [0, 25.0]
        actionDir = (actionDir % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI);
        actionDist = Math.max(0.0, Math.min(25.0, actionDist)); // max speed 25 m/s

        return new Action(actionDir, actionDist);
    }

    public double getLogProbability(Action action, double[] observation) {
        double[] output = actor.forward(observation);
        double meanDir = output[0];
        double meanDist = output[1];
        
        double std = 0.5;
        double term1 = -0.5 * Math.pow((action.getDirectionRad() - meanDir) / std, 2);
        double term2 = -0.5 * Math.pow((action.getDistance() - meanDist) / std, 2);
        double constant = -Math.log(std * Math.sqrt(2 * Math.PI));
        
        return term1 + term2 + 2 * constant;
    }

    public double evaluateState(double[] observation) {
        return critic.forward(observation);
    }

    public void storeTransition(double[] state, double[] action, double reward, double[] nextState, double logProb, boolean done) {
        double val = evaluateState(state);
        buffer.add(state, action, reward, nextState, logProb, done, val);
    }

    public void updatePolicy(double lastValue) {
        PolicyUpdater.computeAdvantagesAndReturns(buffer, lastValue);
        PolicyUpdater.update(actor, critic, buffer);
        buffer.clear();
    }

    public ActorNetwork getActor() {
        return actor;
    }

    public CriticNetwork getCritic() {
        return critic;
    }

    public ExperienceBuffer getBuffer() {
        return buffer;
    }
}
