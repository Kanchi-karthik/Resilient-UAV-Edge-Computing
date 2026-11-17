package org.fog.test.proposed.coordination.secbba;

import java.util.ArrayList;
import java.util.List;

/**
 * SECBBA Bundle - Stores bundle b_i, execution path p_i, and bids y_i for each UAV.
 * 
 * In Yu et al. (Aerospace 2026):
 *  - bundle (b_i): Ordered list of tasks assigned to this UAV in order of addition.
 *  - path (p_i): Ordered sequence of task execution (spatial/temporal path).
 *  - bids: Corresponding marginal reward bids for each task.
 */
public class SECBBABundle {

    private final int uavIndex;
    private final List<Integer> bundle = new ArrayList<>();
    private final List<Integer> path = new ArrayList<>();
    private final List<Double> bids = new ArrayList<>();
    private double totalBid = 0.0;

    public SECBBABundle(int uavIndex) {
        this.uavIndex = uavIndex;
    }

    public int getUavIndex() { return uavIndex; }
    public List<Integer> getBundle() { return bundle; }
    public List<Integer> getPath() { return path; }
    public List<Double> getBids() { return bids; }
    public double getTotalBid() { return totalBid; }

    public void addTask(int taskId, double bid, int insertPos) {
        bundle.add(taskId);
        bids.add(bid);
        totalBid += bid;
        if (insertPos >= 0 && insertPos <= path.size()) {
            path.add(insertPos, taskId);
        } else {
            path.add(taskId);
        }
    }

    public void removeTask(int taskId) {
        int idx = bundle.indexOf(taskId);
        if (idx >= 0) {
            totalBid -= bids.get(idx);
            bundle.remove(idx);
            bids.remove(idx);
        }
        path.remove(Integer.valueOf(taskId));
    }

    public boolean containsTask(int taskId) {
        return bundle.contains(taskId);
    }

    public int size() {
        return bundle.size();
    }

    public void clear() {
        bundle.clear();
        path.clear();
        bids.clear();
        totalBid = 0.0;
    }

    public int getTaskAt(int index) {
        return bundle.get(index);
    }

    public double getBidAt(int index) {
        return bids.get(index);
    }
}
