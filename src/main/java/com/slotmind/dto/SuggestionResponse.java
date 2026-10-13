package com.slotmind.dto;
import com.slotmind.domain.*;
public record SuggestionResponse(String id, String appointmentId, String recommendedSlotId,
        String alternateProviderId, double confidence, String reasoning,
        SuggestionStatus status, TriggerReason triggerReason, String strategySource) {
    public static SuggestionResponse from(RescheduleSuggestion s) {
        return new SuggestionResponse(s.getId(), s.getAppointment().getId(), s.getRecommendedSlot().getId(),
            s.getAlternateProvider() == null ? null : s.getAlternateProvider().getId(),
            s.getConfidence(), s.getReasoning(), s.getStatus(), s.getTriggerReason(), s.getStrategySource());
    }
}
