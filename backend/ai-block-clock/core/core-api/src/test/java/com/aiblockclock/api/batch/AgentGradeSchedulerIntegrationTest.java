package com.aiblockclock.api.batch;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

import static org.assertj.core.api.Assertions.assertThat;

// 재산정이 설정한 때에 잡히는지 본다. 실제로 돌리지는 않는다 — 도는 내용은 AgentGradeSchedulerTest 가 본다.
@Tag("integration")
@SpringBootTest
class AgentGradeSchedulerIntegrationTest {
    @Autowired
    private ScheduledTaskHolder scheduledTaskHolder;

    @Test
    void 등급_재산정은_기본값대로_매일_새벽_3시에_잡힌다() {
        assertThat(scheduledTaskHolder.getScheduledTasks())
                .map(ScheduledTask::getTask)
                .filteredOn(CronTask.class::isInstance)
                .map(CronTask.class::cast)
                .filteredOn(task -> task.toString().endsWith("AgentGradeScheduler.regradeAll"))
                .extracting(CronTask::getExpression)
                .containsExactly("0 0 3 * * *");
    }
}
