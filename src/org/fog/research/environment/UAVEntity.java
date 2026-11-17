package org.fog.research.environment;

import java.util.ArrayList;
import java.util.List;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.research.utils.Vector3D;
import org.fog.research.utils.Constants;

public class UAVEntity extends FogDevice {
    private Vector3D position;
    private final Vector3D initialPosition;
    private double remainingEnergy;
    private final double coverageRadius;
    private final double maxSpeed;
    private final List<Integer> associatedMdIds;
    private final List<ResearchTask> taskQueue;

    public UAVEntity(
            String name,
            FogDeviceCharacteristics characteristics,
            VmAllocationPolicy vmAllocationPolicy,
            List<Storage> storageList,
            double schedulingInterval,
            double uplinkBandwidth, 
            double downlinkBandwidth, 
            double uplinkLatency, 
            double ratePerMips,
            Vector3D initialPosition, 
            double coverageRadius) throws Exception {
        
        super(name, characteristics, vmAllocationPolicy, storageList, schedulingInterval, 
              uplinkBandwidth, downlinkBandwidth, uplinkLatency, ratePerMips);
              
        this.position = initialPosition;
        this.initialPosition = initialPosition;
        this.coverageRadius = coverageRadius;
        this.remainingEnergy = Constants.UAV_BATTERY_CAPACITY_JOULES;
        this.maxSpeed = Constants.UAV_MAX_SPEED;
        this.associatedMdIds = new ArrayList<>();
        this.taskQueue = new ArrayList<>();
    }

    public Vector3D getPosition() {
        return position;
    }

    public void setPosition(Vector3D position) {
        this.position = position;
    }

    public Vector3D getInitialPosition() {
        return initialPosition;
    }

    public double getRemainingEnergy() {
        return remainingEnergy;
    }

    public void setRemainingEnergy(double remainingEnergy) {
        this.remainingEnergy = Math.max(0.0, remainingEnergy);
    }

    public void consumeEnergy(double amount) {
        setRemainingEnergy(this.remainingEnergy - amount);
    }

    public double getCoverageRadius() {
        return coverageRadius;
    }

    public double getMaxSpeed() {
        return maxSpeed;
    }

    public List<Integer> getAssociatedMdIds() {
        return associatedMdIds;
    }

    public List<ResearchTask> getTaskQueue() {
        return taskQueue;
    }

    public void associateMobileDevice(int mdId) {
        if (!associatedMdIds.contains(mdId)) {
            associatedMdIds.add(mdId);
        }
    }

    public void clearAssociations() {
        associatedMdIds.clear();
    }

    public void addTaskToQueue(ResearchTask task) {
        taskQueue.add(task);
    }

    public void removeTaskFromQueue(ResearchTask task) {
        taskQueue.remove(task);
    }
}
