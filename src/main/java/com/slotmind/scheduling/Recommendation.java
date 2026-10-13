package com.slotmind.scheduling;

public record Recommendation(String slotId, double confidence, String reasoning, String source) {
    public Recommendation(String slotId, double confidence, String reasoning) {
        this(slotId, confidence, reasoning, "RULE_BASED");
    }
}
