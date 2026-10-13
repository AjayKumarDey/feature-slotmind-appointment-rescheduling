package com.slotmind.dto;
import com.slotmind.domain.*;
public record ProviderResponse(String id, String name, Specialty specialty, ProviderStatus status) {
    public static ProviderResponse from(Provider p) {
        return new ProviderResponse(p.getId(), p.getName(), p.getSpecialty(), p.getStatus());
    }
}
