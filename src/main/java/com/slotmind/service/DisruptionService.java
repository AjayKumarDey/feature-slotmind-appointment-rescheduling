package com.slotmind.service;

import com.slotmind.domain.*;
import com.slotmind.dto.*;
import com.slotmind.exception.ApiException;
import com.slotmind.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
public class DisruptionService {
    private final ProviderRepository providers;
    private final SlotRepository slots;
    private final AppointmentRepository appointments;
    private final RescheduleSuggestionRepository suggestions;
    private final RescheduleJobRepository jobs;
    private final Clock clock;

    public DisruptionService(ProviderRepository providers, SlotRepository slots, AppointmentRepository appointments,
                             RescheduleSuggestionRepository suggestions, RescheduleJobRepository jobs, Clock clock) {
        this.providers = providers;
        this.slots = slots;
        this.appointments = appointments;
        this.suggestions = suggestions;
        this.jobs = jobs;
        this.clock = clock;
    }

    @Transactional
    public ProviderResponse providerStatus(String id, ProviderStatus status) {
        var p = providers.findByIdForUpdate(id).orElseThrow(() -> missing("Provider"));
        if (p.getStatus() == status) return ProviderResponse.from(p);
        p.changeStatus(status);
        if (status == ProviderStatus.OFFLINE) {
            // Scalar appointment IDs avoid stale appointment entities after waiting for slot locks.
            var affected = appointments.findAffectedIds(id, List.of(AppointmentStatus.CONFIRMED,
                    AppointmentStatus.RESCHEDULED, AppointmentStatus.RESCHEDULE_PENDING));
            var slotIds = new TreeSet<String>();
            for (var item : affected) slotIds.add(item.getSlotId());
            for (var slotId : slotIds) slots.findByIdForUpdate(slotId).orElseThrow(() -> missing("Slot"));
            for (var item : affected) {
                var a = appointments.findByIdForUpdate(item.getId()).orElseThrow(() -> missing("Appointment"));
                if (a.getProvider().getId().equals(id) && a.getStatus() != AppointmentStatus.COMPLETED)
                    enqueue(a, TriggerReason.PROVIDER_OFFLINE);
            }
        }
        return ProviderResponse.from(p);
    }

    @Transactional
    public SlotResponse slotStatus(String id, SlotStatus status) {
        String providerId = slots.findProviderId(id).orElseThrow(() -> missing("Slot"));
        providers.findByIdForUpdate(providerId).orElseThrow(() -> missing("Provider"));
        var slot = slots.findByIdForUpdate(id).orElseThrow(() -> missing("Slot"));
        if (status == SlotStatus.BOOKED)
            throw new ApiException(HttpStatus.BAD_REQUEST, "BOOKED is controlled by booking and suggestion accept");
        if (status == SlotStatus.OPEN) {
            if (appointments.findBySlotId(id).isPresent())
                throw conflict("Slot still has an appointment; reschedule it first");
            slot.reopen();
        } else {
            if (slot.getStatus() == SlotStatus.CANCELLED) return SlotResponse.from(slot);
            slot.cancel();
            var appointmentId = appointments.findBySlotId(id).map(Appointment::getId);
            if (appointmentId.isPresent()) {
                var a = appointments.findByIdForUpdate(appointmentId.get()).orElseThrow(() -> missing("Appointment"));
                if (a.getStatus() != AppointmentStatus.COMPLETED) enqueue(a, TriggerReason.SLOT_CANCELLED);
            }
        }
        return SlotResponse.from(slot);
    }

    private void enqueue(Appointment a, TriggerReason trigger) {
        a.requestReschedule();
        if (suggestions.findByAppointmentIdAndStatus(a.getId(), SuggestionStatus.PENDING).stream().anyMatch(s -> s.getTriggerReason() == trigger))
            return;
        if (jobs.existsByAppointmentIdAndTriggerReasonAndStatusIn(a.getId(), trigger, List.of(JobStatus.QUEUED, JobStatus.RUNNING)))
            return;
        jobs.save(new RescheduleJob("JOB-" + UUID.randomUUID(), a, trigger, LocalDateTime.now(clock)));
    }

    private ApiException missing(String n) {
        return new ApiException(HttpStatus.NOT_FOUND, n + " not found");
    }

    private ApiException conflict(String m) {
        return new ApiException(HttpStatus.CONFLICT, m);
    }
}
