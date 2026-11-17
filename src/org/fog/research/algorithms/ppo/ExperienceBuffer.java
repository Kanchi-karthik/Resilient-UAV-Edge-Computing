package org.fog.research.algorithms.ppo;

import java.util.ArrayList;
import java.util.List;

public class ExperienceBuffer {
    public static class Transition {
        public double[] state;
        public double[] action;
        public double reward;
        public double[] nextState;
        public double logProb;
        public boolean done;
        public double value;
        public double advantage;
        public double returns;

        public Transition(double[] state, double[] action, double reward, double[] nextState, double logProb, boolean done, double value) {
            this.state = state;
            this.action = action;
            this.reward = reward;
            this.nextState = nextState;
            this.logProb = logProb;
            this.done = done;
            this.value = value;
        }
    }

    private final List<Transition> transitions;

    public ExperienceBuffer() {
        this.transitions = new ArrayList<>();
    }

    public void add(double[] state, double[] action, double reward, double[] nextState, double logProb, boolean done, double value) {
        transitions.add(new Transition(state, action, reward, nextState, logProb, done, value));
    }

    public List<Transition> getTransitions() {
        return transitions;
    }

    public void clear() {
        transitions.clear();
    }

    public int size() {
        return transitions.size();
    }
}
