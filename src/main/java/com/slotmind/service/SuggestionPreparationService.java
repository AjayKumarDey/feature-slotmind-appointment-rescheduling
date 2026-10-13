package com.slotmind.service;

import com.slotmind.domain.*;
import com.slotmind.dto.SuggestionResponse;
import com.slotmind.exception.ApiException;
import com.slotmind.repository.*;
import com.slotmind.scheduling.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SuggestionPreparationService {
    public record Snapshot(String appointmentId, String oldSlotId, long version,
                           TriggerReason trigger, SchedulingContext context, SuggestionResponse existing) {
    }

    private final AppointmentRepository appointments;
    private final RescheduleSuggestionRepository suggestions;
    private final SchedulingEngine engine;

    public SuggestionPreparationService(AppointmentRepository appointments, RescheduleSuggestionRepository suggestions, SchedulingEngine engine) {
        this.appointments = appointments;
        this.suggestions = suggestions;
        this.engine = engine;
    }

    @Transactional(readOnly = true)
    public Snapshot prepare(String id, TriggerReason trigger) {
        var a = appointments.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Appointment not found"));
        if (a.getStatus() == AppointmentStatus.COMPLETED)
            throw new ApiException(HttpStatus.CONFLICT, "Completed appointments cannot be rescheduled");
        var existing = suggestions.findByAppointmentIdAndStatus(id, SuggestionStatus.PENDING).stream()
                .filter(s -> s.getTriggerReason() == trigger).findFirst().map(SuggestionResponse::from).orElse(null);
        return new Snapshot(id, a.getSlot().getId(), a.getVersion(), trigger,
                existing == null ? engine.contextFor(a, trigger) : null, existing);
    }
}
