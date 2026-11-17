package org.fog.research.algorithms.ppo;

import java.util.List;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

public class PolicyUpdater {
    private static final double EPSILON = 0.2; // PPO clipping parameter
    private static final double LR_ACTOR = 1e-4;
    private static final double LR_CRITIC = 1e-3;
    private static final double GAMMA = 0.99;
    private static final double GAE_LAMBDA = 0.95;

    public static void computeAdvantagesAndReturns(ExperienceBuffer buffer, double lastValue) {
        List<ExperienceBuffer.Transition> transitions = buffer.getTransitions();
        int size = transitions.size();
        
        double gae = 0.0;
        for (int i = size - 1; i >= 0; i--) {
            ExperienceBuffer.Transition t = transitions.get(i);
            double nextVal = (i == size - 1) ? lastValue : transitions.get(i + 1).value;
            double delta = t.reward + GAMMA * (t.done ? 0.0 : nextVal) - t.value;
            gae = delta + GAMMA * GAE_LAMBDA * (t.done ? 0.0 : 1.0) * gae;
            t.advantage = gae;
            t.returns = t.advantage + t.value;
        }
    }

    public static void update(ActorNetwork actor, CriticNetwork critic, ExperienceBuffer buffer) {
        List<ExperienceBuffer.Transition> transitions = buffer.getTransitions();
        if (transitions.isEmpty()) return;

        for (ExperienceBuffer.Transition t : transitions) {
            // Update Critic
            updateCriticWeights(critic, t.state, t.returns);

            // Update Actor
            updateActorWeights(actor, t.state, t.action, t.advantage, t.logProb);
        }
    }

    private static void updateCriticWeights(CriticNetwork critic, double[] state, double returns) {
        RealVector x = new ArrayRealVector(state);
        RealMatrix w1 = critic.getW1();
        RealMatrix w2 = critic.getW2();
        RealVector b1 = critic.getB1();
        RealVector b2 = critic.getB2();

        RealVector h = w1.operate(x).add(b1);
        RealVector h_act = new ArrayRealVector(h.getDimension());
        for (int i = 0; i < h.getDimension(); i++) {
            h_act.setEntry(i, Math.tanh(h.getEntry(i)));
        }
        double out = w2.operate(h_act).add(b2).getEntry(0);

        // Loss: MSE loss L = (out - returns)^2
        double dL_dout = 2.0 * (out - returns);

        // Backprop output
        RealMatrix dL_dw2 = new Array2DRowRealMatrix(1, h.getDimension());
        for (int j = 0; j < h.getDimension(); j++) {
            dL_dw2.setEntry(0, j, dL_dout * h_act.getEntry(j));
        }
        double dL_db2 = dL_dout;

        RealVector dL_dh_act = w2.getRowVector(0).mapMultiply(dL_dout);

        // Backprop Tanh activation
        RealVector dL_dh = new ArrayRealVector(h.getDimension());
        for (int i = 0; i < h.getDimension(); i++) {
            double tanh_val = h_act.getEntry(i);
            dL_dh.setEntry(i, dL_dh_act.getEntry(i) * (1.0 - tanh_val * tanh_val));
        }

        // Backprop input weights
        RealMatrix dL_dw1 = dL_dh.outerProduct(x);
        RealVector dL_db1 = dL_dh;

        // Apply Gradient Descent
        critic.setWeights(
            w1.subtract(dL_dw1.scalarMultiply(LR_CRITIC)),
            w2.subtract(dL_dw2.scalarMultiply(LR_CRITIC)),
            b1.subtract(dL_db1.mapMultiply(LR_CRITIC)),
            b2.subtract(new ArrayRealVector(new double[]{dL_db2 * LR_CRITIC}))
        );
    }

    private static void updateActorWeights(ActorNetwork actor, double[] state, double[] action, double advantage, double oldLogProb) {
        RealVector x = new ArrayRealVector(state);
        RealMatrix w1 = actor.getW1();
        RealMatrix w2 = actor.getW2();
        RealVector b1 = actor.getB1();
        RealVector b2 = actor.getB2();

        RealVector h = w1.operate(x).add(b1);
        RealVector h_act = new ArrayRealVector(h.getDimension());
        for (int i = 0; i < h.getDimension(); i++) {
            h_act.setEntry(i, Math.tanh(h.getEntry(i)));
        }
        RealVector out = w2.operate(h_act).add(b2);
        
        double meanDir = out.getEntry(0);
        double meanDist = out.getEntry(1);
        
        double logProb = -0.5 * (Math.pow(action[0] - meanDir, 2) + Math.pow(action[1] - meanDist, 2));
        double ratio = Math.exp(logProb - oldLogProb);

        double dJ_dratio = advantage;
        if (advantage > 0 && ratio > 1.0 + EPSILON) {
            dJ_dratio = 0;
        } else if (advantage < 0 && ratio < 1.0 - EPSILON) {
            dJ_dratio = 0;
        }

        double dL_dmeanDir = -dJ_dratio * ratio * (action[0] - meanDir);
        double dL_dmeanDist = -dJ_dratio * ratio * (action[1] - meanDist);
        RealVector dL_dout = new ArrayRealVector(new double[]{dL_dmeanDir, dL_dmeanDist});

        // Backprop output weights
        RealMatrix dL_dw2 = dL_dout.outerProduct(h_act);
        RealVector dL_db2 = dL_dout;

        RealVector dL_dh_act = w2.transpose().operate(dL_dout);

        // Backprop Tanh activation
        RealVector dL_dh = new ArrayRealVector(h.getDimension());
        for (int i = 0; i < h.getDimension(); i++) {
            double tanh_val = h_act.getEntry(i);
            dL_dh.setEntry(i, dL_dh_act.getEntry(i) * (1.0 - tanh_val * tanh_val));
        }

        // Backprop input weights
        RealMatrix dL_dw1 = dL_dh.outerProduct(x);
        RealVector dL_db1 = dL_dh;

        // Apply Gradient Descent
        actor.setWeights(
            w1.subtract(dL_dw1.scalarMultiply(LR_ACTOR)),
            w2.subtract(dL_dw2.scalarMultiply(LR_ACTOR)),
            b1.subtract(dL_db1.mapMultiply(LR_ACTOR)),
            b2.subtract(dL_db2.mapMultiply(LR_ACTOR))
        );
    }
}
