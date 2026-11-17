package org.fog.research.environment;

import java.util.ArrayList;
import java.util.List;
import org.fog.entities.Sensor;
import org.fog.utils.distribution.Distribution;
import org.fog.research.utils.Vector2D;
import org.fog.research.utils.ResearchMath;

public class MobileDevice extends Sensor {
    private Vector2D position;
    private final Vector2D initialPosition;
    private final double speed;
    private double directionRad;
    private int connectedUavId = -1;
    private final List<ResearchTask> taskBuffer;

    public MobileDevice(String name, String tupleType, int userId, String appId, 
                        Distribution transmitDistribution, Vector2D initialPosition, 
                        double speed, double initialDirectionRad) {
        super(name, tupleType, userId, appId, transmitDistribution);
        this.position = initialPosition;
        this.initialPosition = initialPosition;
        this.speed = speed;
        this.directionRad = initialDirectionRad;
        this.taskBuffer = new ArrayList<>();
    }

    public Vector2D getPosition() {
        return position;
    }

    public void setPosition(Vector2D position) {
        this.position = position;
    }

    public Vector2D getInitialPosition() {
        return initialPosition;
    }

    public double getSpeed() {
        return speed;
    }

    public double getDirectionRad() {
        return directionRad;
    }

    public void setDirectionRad(double directionRad) {
        this.directionRad = directionRad;
    }

    public int getConnectedUavId() {
        return connectedUavId;
    }

    public void setConnectedUavId(int connectedUavId) {
        this.connectedUavId = connectedUavId;
        setGatewayDeviceId(connectedUavId);
    }

    public List<ResearchTask> getTaskBuffer() {
        return taskBuffer;
    }

    public void addTaskToBuffer(ResearchTask task) {
        taskBuffer.add(task);
    }

    public void removeTaskFromBuffer(ResearchTask task) {
        taskBuffer.remove(task);
    }

    public void updatePosition(double timeStepSec, double minX, double maxX, double minY, double maxY) {
        double newX = position.getX() + speed * timeStepSec * Math.cos(directionRad);
        double newY = position.getY() + speed * timeStepSec * Math.sin(directionRad);
        
        if (newX < minX || newX > maxX) {
            directionRad = Math.PI - directionRad;
            newX = ResearchMath.clamp(newX, minX, maxX);
        }
        if (newY < minY || newY > maxY) {
            directionRad = -directionRad;
            newY = ResearchMath.clamp(newY, minY, maxY);
        }
        position = new Vector2D(newX, newY);
    }
}
