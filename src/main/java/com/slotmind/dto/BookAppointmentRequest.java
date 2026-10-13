package com.slotmind.dto;
import com.slotmind.domain.AppointmentType;
import jakarta.validation.constraints.*;
public record BookAppointmentRequest(
    @NotBlank @Size(max = 120) String patientName,
    @NotNull AppointmentType appointmentType,
    @NotBlank @Size(max = 64) String providerId,
    @NotBlank @Size(max = 64) String slotId
) {}
