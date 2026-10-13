package com.slotmind.controller;
import com.slotmind.domain.TriggerReason;
import com.slotmind.dto.*;
import com.slotmind.service.RescheduleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController
public class RescheduleController {
    private final RescheduleService service;
    public RescheduleController(RescheduleService service) { this.service = service; }
    @PostMapping("/appointments/{id}/suggest")
    public SuggestionResponse suggest(@PathVariable String id) { return service.suggest(id, TriggerReason.INITIAL); }
    @PatchMapping("/suggestions/{id}")
    public SuggestionResponse decide(@PathVariable String id, @Valid @RequestBody DecideSuggestionRequest request) {
        return service.decide(id, request.status());
    }
}
