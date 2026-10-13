package com.slotmind.service;

import com.slotmind.domain.*;
import com.slotmind.dto.SuggestionResponse;
import com.slotmind.exception.ApiException;
import com.slotmind.repository.*;
import com.slotmind.scheduling.Recommendation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.UUID;

@Service
public class SuggestionPersistenceService {
    private final AppointmentRepository appointments;
    private final ProviderRepository providers;
    private final SlotRepository slots;
    private final RescheduleSuggestionRepository suggestions;
    private final Clock clock;

    public SuggestionPersistenceService(AppointmentRepository appointments, ProviderRepository providers,
                                        SlotRepository slots, RescheduleSuggestionRepository suggestions, Clock clock) {
        this.appointments = appointments;
        this.providers = providers;
        this.slots = slots;
        this.suggestions = suggestions;
        this.clock = clock;
    }

    @Transactional
    public SuggestionResponse persist(SuggestionPreparationService.Snapshot snapshot, Recommendation recommendation) {
        var candidate = snapshot.context().candidates().stream().filter(c -> c.slotId().equals(recommendation.slotId()))
                .findFirst().orElseThrow(() -> conflict("Strategy recommended an ineligible slot"));
        providers.findByIdForUpdate(candidate.providerId()).orElseThrow(() -> conflict("Provider no longer exists"));
        var target = slots.findByIdForUpdate(candidate.slotId()).orElseThrow(() -> conflict("Slot no longer exists"));
        var a = appointments.findByIdForUpdate(snapshot.appointmentId()).orElseThrow(() -> conflict("Appointment no longer exists"));
        var existing = suggestions.findByAppointmentIdAndStatus(a.getId(), SuggestionStatus.PENDING).stream()
                .filter(s -> s.getTriggerReason() == snapshot.trigger()).findFirst();
        if (existing.isPresent()) return SuggestionResponse.from(existing.get());
        if (a.getVersion() != snapshot.version() || !a.getSlot().getId().equals(snapshot.oldSlotId()))
            throw conflict("Appointment changed while generating suggestion. Try again.");
        if (a.getStatus() == AppointmentStatus.COMPLETED || target.getStatus() != SlotStatus.OPEN
                || !target.getStartTime().isAfter(LocalDateTime.now(clock))
                || target.getProvider().getStatus() == ProviderStatus.OFFLINE
                || target.getProvider().getSpecialty() != a.getProvider().getSpecialty()
                || target.getDurationMinutes() < a.getSlot().getDurationMinutes())
            throw conflict("Replacement slot is no longer available or compatible");
        a.requestReschedule();
        var alternate = target.getProvider().getId().equals(a.getProvider().getId()) ? null : target.getProvider();
        var s = new RescheduleSuggestion("SUG-" + UUID.randomUUID(), a, target, alternate,
                recommendation.confidence(), recommendation.reasoning(), SuggestionStatus.PENDING, snapshot.trigger());
        s.recordSource(recommendation.source());
        return SuggestionResponse.from(suggestions.saveAndFlush(s));
    }

    private ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }
}
