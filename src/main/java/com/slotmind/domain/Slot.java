package com.slotmind.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "slots")
public class Slot {
    @Id
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private int durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SlotStatus status;

    @Version
    private long version;

    protected Slot() {}

    public Slot(String id, Provider provider, LocalDateTime startTime, int durationMinutes, SlotStatus status) {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(provider, "provider is required");
        Objects.requireNonNull(startTime, "startTime is required");
        Objects.requireNonNull(status, "status is required");
        if (durationMinutes <= 0) throw new IllegalArgumentException("Duration must be positive");
        this.id = id;
        this.provider = provider;
        this.startTime = startTime;
        this.durationMinutes = durationMinutes;
        this.status = status;
    }

    public void bookAt(LocalDateTime now) {
        if (status != SlotStatus.OPEN) throw new IllegalStateException("Slot is not open");
        if (!startTime.isAfter(now)) throw new IllegalStateException("Slot must be in the future");
        if (provider.getStatus() == ProviderStatus.OFFLINE) throw new IllegalStateException("Provider is offline");
        status = SlotStatus.BOOKED;
    }

    public void releaseBooking() {
        if (status == SlotStatus.CANCELLED) return;
        if (status != SlotStatus.BOOKED) throw new IllegalStateException("Old slot is not booked");
        status = SlotStatus.OPEN;
    }

    public void cancel() { status = SlotStatus.CANCELLED; }
    public void reopen() {
        if (status == SlotStatus.BOOKED) throw new IllegalStateException("Cannot reopen a booked slot");
        status = SlotStatus.OPEN;
    }

    public String getId() { return id; }
    public Provider getProvider() { return provider; }
    public LocalDateTime getStartTime() { return startTime; }
    public int getDurationMinutes() { return durationMinutes; }
    public SlotStatus getStatus() { return status; }
    public long getVersion() { return version; }
}
