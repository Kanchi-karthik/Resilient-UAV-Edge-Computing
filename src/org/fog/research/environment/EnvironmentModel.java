package org.fog.research.environment;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class EnvironmentModel {

	private final Map<Integer, UavState> uavsById;
	private final Map<Integer, MobileDeviceState> mdsById;
	private final Map<Integer, TaskDemand> tasksById;

	public EnvironmentModel() {
		this.uavsById = new LinkedHashMap<Integer, UavState>();
		this.mdsById = new LinkedHashMap<Integer, MobileDeviceState>();
		this.tasksById = new LinkedHashMap<Integer, TaskDemand>();
	}

	public void upsertUav(UavState uav) {
		uavsById.put(uav.getUavId(), uav);
	}

	public void upsertMobileDevice(MobileDeviceState md) {
		mdsById.put(md.getMdId(), md);
	}

	public void upsertTask(TaskDemand task) {
		tasksById.put(task.getTaskId(), task);
	}

	public UavState getUav(int uavId) {
		return uavsById.get(uavId);
	}

	public MobileDeviceState getMobileDevice(int mdId) {
		return mdsById.get(mdId);
	}

	public TaskDemand getTask(int taskId) {
		return tasksById.get(taskId);
	}

	public Collection<UavState> getUavs() {
		return Collections.unmodifiableCollection(uavsById.values());
	}

	public Collection<MobileDeviceState> getMobileDevices() {
		return Collections.unmodifiableCollection(mdsById.values());
	}

	public Collection<TaskDemand> getTasks() {
		return Collections.unmodifiableCollection(tasksById.values());
	}
}
