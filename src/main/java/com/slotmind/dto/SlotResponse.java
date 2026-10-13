package com.slotmind.dto;
import com.slotmind.domain.*;
import java.time.LocalDateTime;
public record SlotResponse(String id, String providerId, LocalDateTime startTime,
        int durationMinutes, SlotStatus status) {
    public static SlotResponse from(Slot s) {
        return new SlotResponse(s.getId(), s.getProvider().getId(), s.getStartTime(), s.getDurationMinutes(), s.getStatus());
    }
}
