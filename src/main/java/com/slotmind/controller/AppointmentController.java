package com.slotmind.controller;

import com.slotmind.dto.*;
import com.slotmind.service.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/appointments")
public class AppointmentController {
    private final AppointmentService appointments;

    public AppointmentController(AppointmentService appointments) {
        this.appointments = appointments;
    }

    @PostMapping
    public ResponseEntity<AppointmentResponse> book(@Valid @RequestBody BookAppointmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(appointments.book(request));
    }
}
