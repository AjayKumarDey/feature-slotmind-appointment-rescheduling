package com.slotmind.dto;
import com.slotmind.domain.*;
import java.time.LocalDateTime;
public record AppointmentResponse(String id, String patientName, AppointmentType appointmentType,
        String providerId, String providerName, String slotId, LocalDateTime startTime,
        int durationMinutes, AppointmentStatus status) {
    public static AppointmentResponse from(Appointment a) {
        return new AppointmentResponse(a.getId(), a.getPatientName(), a.getAppointmentType(),
            a.getProvider().getId(), a.getProvider().getName(), a.getSlot().getId(),
            a.getSlot().getStartTime(), a.getSlot().getDurationMinutes(), a.getStatus());
    }
}
