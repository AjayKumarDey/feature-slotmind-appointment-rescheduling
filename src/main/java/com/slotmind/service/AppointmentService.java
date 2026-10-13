package com.slotmind.service;

import com.slotmind.domain.*;
import com.slotmind.dto.*;
import com.slotmind.exception.ApiException;
import com.slotmind.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AppointmentService {
    private final ProviderRepository providers;
    private final SlotRepository slots;
    private final AppointmentRepository appointments;
    private final Clock clock;

    public AppointmentService(ProviderRepository providers, SlotRepository slots,
                              AppointmentRepository appointments, Clock clock) {
        this.providers = providers;
        this.slots = slots;
        this.appointments = appointments;
        this.clock = clock;
    }

    @Transactional
    public AppointmentResponse book(BookAppointmentRequest request) {
        // Lock provider first, then slot. Future offline/accept paths must use the same order.
        var provider = providers.findByIdForUpdate(request.providerId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Provider not found"));
        var slot = slots.findByIdForUpdate(request.slotId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Slot not found"));
        if (!slot.getProvider().getId().equals(provider.getId()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Selected slot does not belong to selected provider");
        if (provider.getStatus() == ProviderStatus.OFFLINE)
            throw new ApiException(HttpStatus.CONFLICT, "Provider is offline");
        try {
            slot.bookAt(LocalDateTime.now(clock));
        } catch (IllegalStateException e) {
            throw new ApiException(HttpStatus.CONFLICT, e.getMessage());
        }
        var appointment = Appointment.booked("APT-" + UUID.randomUUID(), request.patientName(), request.appointmentType(), slot);
        appointments.saveAndFlush(appointment);
        // Slot is managed by JPA; its BOOKED status is saved in the same transaction.
        return AppointmentResponse.from(appointment);
    }
}
