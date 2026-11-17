package org.fog.research.algorithms.ppo;

import java.util.Random;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

public class CriticNetwork {
    private RealMatrix w1, w2;
    private RealVector b1, b2;
    private final int inputDim;
    private final int hiddenDim;
    private final Random rand;

    public CriticNetwork(int inputDim, int hiddenDim) {
        this.inputDim = inputDim;
        this.hiddenDim = hiddenDim;
        this.rand = new Random();
        
        double limit1 = Math.sqrt(6.0 / (inputDim + hiddenDim));
        w1 = new Array2DRowRealMatrix(hiddenDim, inputDim);
        b1 = new ArrayRealVector(hiddenDim);
        for (int i = 0; i < hiddenDim; i++) {
            b1.setEntry(i, 0.0);
            for (int j = 0; j < inputDim; j++) {
                w1.setEntry(i, j, (rand.nextDouble() * 2 - 1) * limit1);
            }
        }
        
        double limit2 = Math.sqrt(6.0 / (hiddenDim + 1));
        w2 = new Array2DRowRealMatrix(1, hiddenDim);
        b2 = new ArrayRealVector(1);
        b2.setEntry(0, 0.0);
        for (int j = 0; j < hiddenDim; j++) {
            w2.setEntry(0, j, (rand.nextDouble() * 2 - 1) * limit2);
        }
    }

    public double forward(double[] input) {
        RealVector x = new ArrayRealVector(input);
        RealVector h = w1.operate(x).add(b1);
        for (int i = 0; i < h.getDimension(); i++) {
            h.setEntry(i, Math.tanh(h.getEntry(i)));
        }
        RealVector out = w2.operate(h).add(b2);
        return out.getEntry(0);
    }

    public RealMatrix getW1() { return w1; }
    public RealMatrix getW2() { return w2; }
    public RealVector getB1() { return b1; }
    public RealVector getB2() { return b2; }

    public void setWeights(RealMatrix w1, RealMatrix w2, RealVector b1, RealVector b2) {
        this.w1 = w1;
        this.w2 = w2;
        this.b1 = b1;
        this.b2 = b2;
    }
}
