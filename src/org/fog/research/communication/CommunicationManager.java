package org.fog.research.communication;

import org.fog.research.environment.MobileDeviceState;
import org.fog.research.environment.TaskDemand;
import org.fog.research.environment.UavState;

public class CommunicationManager {

	private final CommunicationModel communicationModel;

	public CommunicationManager() {
		this.communicationModel = new CommunicationModel();
	}

	public CommunicationManager(CommunicationModel communicationModel) {
		this.communicationModel = communicationModel;
	}

	public CommunicationSnapshot evaluate(UavState uav, MobileDeviceState md, TaskDemand task) {
		return communicationModel.evaluate(uav, md, task);
	}

	public CommunicationModel getCommunicationModel() {
		return communicationModel;
	}
}
