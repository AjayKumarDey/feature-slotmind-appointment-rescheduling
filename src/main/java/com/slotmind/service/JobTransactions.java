package com.slotmind.service;

import com.slotmind.domain.*;
import com.slotmind.dto.JobResponse;
import com.slotmind.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
public class JobTransactions {
    public record Work(String id, String appointmentId, String slotId, TriggerReason trigger, int attempt) {
    }

    private final RescheduleJobRepository jobs;
    private final AppointmentRepository appointments;
    private final Clock clock;

    public JobTransactions(RescheduleJobRepository jobs, AppointmentRepository appointments, Clock clock) {
        this.jobs = jobs;
        this.appointments = appointments;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<JobResponse> list() {
        return jobs.findAll().stream().map(JobResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<String> readyIds() {
        var now = LocalDateTime.now(clock);
        return jobs.findAll().stream().filter(j -> ready(j, now)).limit(20).map(RescheduleJob::getId).toList();
    }

    private boolean ready(RescheduleJob j, LocalDateTime now) {
        return j.getStatus() == JobStatus.QUEUED && !j.getAvailableAt().isAfter(now)
                || j.getStatus() == JobStatus.RUNNING && j.getStartedAt().isBefore(now.minusMinutes(2));
    }

    @Transactional
    public Work claim(String id) {
        var j = jobs.findByIdForUpdate(id).orElse(null);
        var now = LocalDateTime.now(clock);
        if (j == null || !ready(j, now)) return null;
        if (j.getAttempts() >= 3) {
            j.finish(JobStatus.FAILED, "Retry limit reached", now);
            return null;
        }
        var a = appointments.findById(j.getAppointmentId()).orElse(null);
        if (a == null || a.getStatus() == AppointmentStatus.COMPLETED || !a.getSlot().getId().equals(j.getOriginalSlotId())) {
            j.finish(JobStatus.DONE, "Appointment changed; job is obsolete", now);
            return null;
        }
        j.claim(now);
        return new Work(j.getId(), j.getAppointmentId(), j.getOriginalSlotId(), j.getTriggerReason(), j.getAttempts());
    }

    @Transactional
    public void finish(Work work, JobStatus status, String message) {
        var j = jobs.findByIdForUpdate(work.id()).orElseThrow();
        if (j.getStatus() != JobStatus.RUNNING || j.getAttempts() != work.attempt()) return;
        j.finish(status, message, LocalDateTime.now(clock).plusSeconds(5));
    }
}
