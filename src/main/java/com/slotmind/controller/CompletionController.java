package com.slotmind.controller;
import com.slotmind.dto.AppointmentResponse;
import com.slotmind.service.CompletionService;
import org.springframework.web.bind.annotation.*;
@RestController public class CompletionController {
    private final CompletionService service;
    public CompletionController(CompletionService service){this.service=service;}
    @PatchMapping("/appointments/{id}/complete") public AppointmentResponse complete(@PathVariable String id){return service.complete(id);}
}
