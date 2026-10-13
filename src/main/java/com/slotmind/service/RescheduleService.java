package com.slotmind.service;

import com.slotmind.domain.*;
import com.slotmind.dto.*;
import com.slotmind.exception.ApiException;
import com.slotmind.repository.*;
import com.slotmind.scheduling.SchedulingEngine;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
public class RescheduleService {
    private final AppointmentRepository appointments;
    private final ProviderRepository providers;
    private final SlotRepository slots;
    private final RescheduleSuggestionRepository suggestions;
    private final SchedulingEngine engine;
    private final Clock clock;
    private final SuggestionPreparationService preparation;
    private final SuggestionPersistenceService persistence;

    public RescheduleService(AppointmentRepository appointments, ProviderRepository providers,
                             SlotRepository slots, RescheduleSuggestionRepository suggestions, SchedulingEngine engine, Clock clock, SuggestionPreparationService preparation, SuggestionPersistenceService persistence) {
        this.appointments = appointments;
        this.providers = providers;
        this.slots = slots;
        this.suggestions = suggestions;
        this.engine = engine;
        this.clock = clock;
        this.preparation = preparation;
        this.persistence = persistence;
    }

    public SuggestionResponse suggest(String appointmentId, TriggerReason trigger) {
        // No transaction during the remote LLM call.
        var snapshot = preparation.prepare(appointmentId, trigger);
        if (snapshot.existing() != null) return snapshot.existing();
        var recommendations = engine.recommend(snapshot.context());
        if (recommendations.isEmpty()) throw conflict("No suitable replacement slot is available");
        return persistence.persist(snapshot, recommendations.get(0));
    }

    @Transactional
    public SuggestionResponse decide(String id, DecideSuggestionRequest.Decision decision) {
        var ids = suggestions.findLockIds(id).orElseThrow(() -> missing("Suggestion"));
        // Global lock order: sorted providers, sorted slots, appointment, suggestion.
        for (String providerId : new TreeSet<>(List.of(ids.getOldProviderId(), ids.getNewProviderId())))
            providers.findByIdForUpdate(providerId).orElseThrow(() -> missing("Provider"));
        var lockedSlots = new HashMap<String, Slot>();
        for (String slotId : new TreeSet<>(List.of(ids.getOldSlotId(), ids.getNewSlotId())))
            lockedSlots.put(slotId, slots.findByIdForUpdate(slotId).orElseThrow(() -> missing("Slot")));
        var a = appointments.findByIdForUpdate(ids.getAppointmentId()).orElseThrow(() -> missing("Appointment"));
        var s = suggestions.findByIdForUpdate(id).orElseThrow(() -> missing("Suggestion"));
        // A previous accept may have moved this appointment while we waited.
        if (!a.getSlot().getId().equals(ids.getOldSlotId()) || !a.getProvider().getId().equals(ids.getOldProviderId()))
            throw conflict("Appointment changed. Refresh suggestions.");
        if (s.getStatus() != SuggestionStatus.PENDING) throw conflict("Suggestion was already decided");
        if (a.getStatus() != AppointmentStatus.RESCHEDULE_PENDING) throw conflict("Appointment is not pending");
        try {
            if (decision == DecideSuggestionRequest.Decision.REJECTED) {
                s.reject();
                if (suggestions.findByAppointmentIdAndStatus(a.getId(), SuggestionStatus.PENDING).isEmpty())
                    a.restoreAfterRejection();
            } else {
                var oldSlot = lockedSlots.get(ids.getOldSlotId());
                var target = lockedSlots.get(ids.getNewSlotId());
                if (target.getId().equals(oldSlot.getId())) throw conflict("Replacement must be a different slot");
                if (target.getProvider().getSpecialty() != a.getProvider().getSpecialty()
                        || target.getDurationMinutes() < oldSlot.getDurationMinutes())
                    throw conflict("Replacement slot is no longer compatible");
                target.bookAt(LocalDateTime.now(clock));
                oldSlot.releaseBooking();
                a.rescheduleTo(target);
                s.accept();
                // Competing proposals for this appointment become obsolete after moving it.
                for (var sibling : suggestions.findByAppointmentIdAndStatus(a.getId(), SuggestionStatus.PENDING))
                    sibling.reject();
            }
        } catch (IllegalStateException e) {
            throw conflict(e.getMessage());
        }
        suggestions.flush();
        return SuggestionResponse.from(s);
    }

    private ApiException missing(String type) {
        return new ApiException(HttpStatus.NOT_FOUND, type + " not found");
    }

    private ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }
}
