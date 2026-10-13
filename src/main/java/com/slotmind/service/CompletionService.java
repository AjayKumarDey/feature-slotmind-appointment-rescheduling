package com.slotmind.service;

import com.slotmind.dto.AppointmentResponse;
import com.slotmind.exception.ApiException;
import com.slotmind.repository.AppointmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;

@Service
public class CompletionService {
    private final AppointmentRepository appointments;

    public CompletionService(AppointmentRepository appointments) {
        this.appointments = appointments;
    }

    @Transactional
    public AppointmentResponse complete(String id) {
        var a = appointments.findByIdForUpdate(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Appointment not found"));
        try {
            a.complete();
        } catch (IllegalStateException e) {
            throw new ApiException(HttpStatus.CONFLICT, e.getMessage());
        }
        return AppointmentResponse.from(a);
    }
}
