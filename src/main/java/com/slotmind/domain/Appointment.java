package com.slotmind.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "appointments")
public class Appointment {
    @Id
    private String id;

    @Column(nullable = false, length = 120)
    private String patientName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentType appointmentType;

    @ManyToOne(optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @OneToOne(optional = false)
    @JoinColumn(name = "slot_id", nullable = false, unique = true)
    private Slot slot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status;

    @Enumerated(EnumType.STRING)
    private AppointmentStatus statusBeforePending;

    @Version
    private long version;

    protected Appointment() {}

    public Appointment(String id, String patientName, AppointmentType appointmentType, Provider provider, Slot slot, AppointmentStatus status) {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(patientName, "patientName is required");
        Objects.requireNonNull(appointmentType, "appointmentType is required");
        Objects.requireNonNull(provider, "provider is required");
        Objects.requireNonNull(slot, "slot is required");
        Objects.requireNonNull(status, "status is required");
        if (!provider.getId().equals(slot.getProvider().getId())) throw new IllegalArgumentException("Provider must own slot");
        this.id = id;
        this.patientName = patientName;
        this.appointmentType = appointmentType;
        this.provider = provider;
        this.slot = slot;
        this.status = status;
    }

    public static Appointment booked(String id, String patientName, AppointmentType type, Slot slot) {
        if (slot.getStatus() != SlotStatus.BOOKED) throw new IllegalStateException("Slot must be booked first");
        if (patientName == null || patientName.isBlank() || patientName.trim().length() > 120)
            throw new IllegalArgumentException("Patient name must contain 1 to 120 characters");
        return new Appointment(id, patientName.trim(), type, slot.getProvider(), slot, AppointmentStatus.CONFIRMED);
    }

    public void requestReschedule() {
        if (status == AppointmentStatus.RESCHEDULE_PENDING) return;
        if (status != AppointmentStatus.CONFIRMED && status != AppointmentStatus.RESCHEDULED)
            throw new IllegalStateException("Only active appointments can be rescheduled");
        statusBeforePending = status;
        status = AppointmentStatus.RESCHEDULE_PENDING;
    }
    public void rescheduleTo(Slot target) {
        if (status != AppointmentStatus.RESCHEDULE_PENDING) throw new IllegalStateException("Appointment is not pending");
        if (target.getStatus() != SlotStatus.BOOKED) throw new IllegalStateException("Replacement slot must be booked");
        if (target.getId().equals(slot.getId())) throw new IllegalStateException("Replacement must be a different slot");
        if (target.getProvider().getSpecialty() != provider.getSpecialty()) throw new IllegalStateException("Specialty must match");
        if (target.getDurationMinutes() < slot.getDurationMinutes()) throw new IllegalStateException("Replacement slot is too short");
        provider = target.getProvider(); slot = target; status = AppointmentStatus.RESCHEDULED;
        statusBeforePending = null;
    }
    public void restoreAfterRejection() {
        if (status != AppointmentStatus.RESCHEDULE_PENDING) throw new IllegalStateException("Appointment is not pending");
        // Rejecting a proposal cannot repair a real disruption.
        if (provider.getStatus() != ProviderStatus.OFFLINE && slot.getStatus() == SlotStatus.BOOKED) {
            status = statusBeforePending == AppointmentStatus.RESCHEDULED ? AppointmentStatus.RESCHEDULED : AppointmentStatus.CONFIRMED;
            statusBeforePending = null;
        }
    }

    public void complete() {
        if (status != AppointmentStatus.CONFIRMED && status != AppointmentStatus.RESCHEDULED)
            throw new IllegalStateException("Only a valid active appointment can be completed");
        status = AppointmentStatus.COMPLETED;
    }

    public String getId() { return id; }
    public String getPatientName() { return patientName; }
    public AppointmentType getAppointmentType() { return appointmentType; }
    public Provider getProvider() { return provider; }
    public Slot getSlot() { return slot; }
    public AppointmentStatus getStatus() { return status; }
    public long getVersion() { return version; }
}
