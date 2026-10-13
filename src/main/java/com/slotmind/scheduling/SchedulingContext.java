package com.slotmind.scheduling;

import com.slotmind.domain.*;

import java.time.LocalDateTime;
import java.util.List;

public record SchedulingContext(String appointmentId, AppointmentType appointmentType,
                                String originalProviderId, Specialty specialty, LocalDateTime originalStart,
                                int requiredDurationMinutes, TriggerReason triggerReason, List<Candidate> candidates) {
    public record Candidate(String slotId, String providerId, String providerName, LocalDateTime startTime,
                            int durationMinutes) {
    }

    public SchedulingContext {
        candidates = List.copyOf(candidates);
    }
}
