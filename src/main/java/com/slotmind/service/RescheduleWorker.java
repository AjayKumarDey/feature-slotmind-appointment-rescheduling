package com.slotmind.service;

import com.slotmind.domain.JobStatus;
import com.slotmind.exception.ApiException;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "worker.enabled", havingValue = "true", matchIfMissing = true)
public class RescheduleWorker {
    private static final Logger log = LoggerFactory.getLogger(RescheduleWorker.class);
    private final JobTransactions jobs;
    private final RescheduleService suggestions;

    public RescheduleWorker(JobTransactions jobs, RescheduleService suggestions) {
        this.jobs = jobs;
        this.suggestions = suggestions;
    }

    @Scheduled(fixedDelayString = "${worker.poll-ms:1500}")
    public void tick() {
        for (String id : jobs.readyIds()) process(id);
    }

    public void process(String id) {
        var work = jobs.claim(id);
        if (work == null) return;
        try {
            suggestions.suggest(work.appointmentId(), work.trigger());
            jobs.finish(work, JobStatus.DONE, "Suggestion ready for staff approval");
        } catch (ApiException e) {
            boolean noOptions = e.getMessage().equals("No suitable replacement slot is available");
            jobs.finish(work, noOptions ? JobStatus.NO_OPTIONS : work.attempt() < 3 ? JobStatus.QUEUED : JobStatus.FAILED, e.getMessage());
        } catch (Exception e) {
            log.error("Background rescheduling failed for job {} ({})", id, e.getClass().getSimpleName());
            jobs.finish(work, work.attempt() < 3 ? JobStatus.QUEUED : JobStatus.FAILED, "Scheduling failed; retry or request a manual suggestion");
        }
    }
}
