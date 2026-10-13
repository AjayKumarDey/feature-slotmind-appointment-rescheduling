package com.slotmind.service;

import com.slotmind.domain.AppointmentStatus;
import com.slotmind.dto.*;
import com.slotmind.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ClinicQueryService {
    private final ProviderRepository providers;
    private final SlotRepository slots;
    private final AppointmentRepository appointments;
    private final RescheduleSuggestionRepository suggestions;

    public ClinicQueryService(ProviderRepository providers, SlotRepository slots,
                              AppointmentRepository appointments, RescheduleSuggestionRepository suggestions) {
        this.providers = providers;
        this.slots = slots;
        this.appointments = appointments;
        this.suggestions = suggestions;
    }

    public List<ProviderResponse> providers() {
        return providers.findAll().stream().map(ProviderResponse::from).toList();
    }

    public List<SlotResponse> slots() {
        return slots.findAll().stream().map(SlotResponse::from).toList();
    }

    public List<AppointmentResponse> appointments(AppointmentStatus status) {
        return appointments.findAll().stream().filter(a -> status == null || a.getStatus() == status)
                .map(AppointmentResponse::from).toList();
    }

    public List<SuggestionResponse> suggestions() {
        return suggestions.findAll().stream().map(SuggestionResponse::from).toList();
    }
}
