package org.fog.research.metrics;

import java.util.List;
import org.fog.research.environment.ResearchTask;
import org.fog.research.utils.TaskStatus;

public class CompletedTasksCounter {

    public static int countCompleted(List<ResearchTask> allTasks) {
        int count = 0;
        for (ResearchTask task : allTasks) {
            if (task.getResearchTaskStatus() == TaskStatus.COMPLETED) {
                count++;
            }
        }
        return count;
    }

    public static double calculateSuccessRate(List<ResearchTask> allTasks) {
        if (allTasks.isEmpty()) {
            return 0.0;
        }
        int completed = countCompleted(allTasks);
        return (double) completed / allTasks.size();
    }
}
