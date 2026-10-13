package com.slotmind.dto;
import jakarta.validation.constraints.NotNull;
public record DecideSuggestionRequest(@NotNull Decision status) {
    public enum Decision { ACCEPTED, REJECTED }
}
