package com.slotmind.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "reschedule_suggestions")
public class RescheduleSuggestion {
    @Id
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @ManyToOne(optional = false)
    @JoinColumn(name = "recommended_slot_id", nullable = false)
    private Slot recommendedSlot;

    @ManyToOne
    @JoinColumn(name = "alternate_provider_id")
    private Provider alternateProvider;

    @Column(nullable = false)
    private double confidence;

    @Column(nullable = false, length = 2000)
    private String reasoning;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SuggestionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TriggerReason triggerReason;

    @Column(nullable = false)
    private String strategySource = "RULE_BASED";

    @Version
    private long version;

    protected RescheduleSuggestion() {}

    public RescheduleSuggestion(String id, Appointment appointment, Slot recommendedSlot, Provider alternateProvider, double confidence, String reasoning, SuggestionStatus status, TriggerReason triggerReason) {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(appointment, "appointment is required");
        Objects.requireNonNull(recommendedSlot, "recommendedSlot is required");
        Objects.requireNonNull(reasoning, "reasoning is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(triggerReason, "triggerReason is required");
        if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) throw new IllegalArgumentException("Confidence must be between 0 and 1");
        if (alternateProvider != null && !alternateProvider.getId().equals(recommendedSlot.getProvider().getId())) throw new IllegalArgumentException("Alternate provider must own recommended slot");
        this.id = id;
        this.appointment = appointment;
        this.recommendedSlot = recommendedSlot;
        this.alternateProvider = alternateProvider;
        this.confidence = confidence;
        this.reasoning = reasoning;
        this.status = status;
        this.triggerReason = triggerReason;
    }

    public void accept() {
        requirePending(); status = SuggestionStatus.ACCEPTED;
    }
    public void reject() {
        requirePending(); status = SuggestionStatus.REJECTED;
    }
    private void requirePending() {
        if (status != SuggestionStatus.PENDING) throw new IllegalStateException("Suggestion was already decided");
    }

    public void recordSource(String source) { strategySource = Objects.requireNonNull(source); }
    public String getStrategySource() { return strategySource; }
    public String getId() { return id; }
    public Appointment getAppointment() { return appointment; }
    public Slot getRecommendedSlot() { return recommendedSlot; }
    public Provider getAlternateProvider() { return alternateProvider; }
    public double getConfidence() { return confidence; }
    public String getReasoning() { return reasoning; }
    public SuggestionStatus getStatus() { return status; }
    public TriggerReason getTriggerReason() { return triggerReason; }
    public long getVersion() { return version; }
}
