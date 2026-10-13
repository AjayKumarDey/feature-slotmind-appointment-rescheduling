package com.slotmind.controller;

import com.slotmind.domain.AppointmentStatus;
import com.slotmind.dto.*;
import com.slotmind.service.ClinicQueryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class ClinicReadController {
    private final ClinicQueryService queries;

    public ClinicReadController(ClinicQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/providers")
    public List<ProviderResponse> providers() {
        return queries.providers();
    }

    @GetMapping("/slots")
    public List<SlotResponse> slots() {
        return queries.slots();
    }

    @GetMapping("/appointments")
    public List<AppointmentResponse> appointments(
            @RequestParam(required = false) AppointmentStatus status) {
        return queries.appointments(status);
    }

    @GetMapping("/suggestions")
    public List<SuggestionResponse> suggestions() {
        return queries.suggestions();
    }
}
