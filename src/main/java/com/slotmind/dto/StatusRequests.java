package com.slotmind.dto;
import com.slotmind.domain.*;
import jakarta.validation.constraints.NotNull;
public final class StatusRequests {
    private StatusRequests() {}
    public record ProviderUpdate(@NotNull ProviderStatus status) {}
    public record SlotUpdate(@NotNull SlotStatus status) {}
}
