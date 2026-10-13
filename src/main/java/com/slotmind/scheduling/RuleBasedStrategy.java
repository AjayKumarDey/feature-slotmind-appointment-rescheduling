package com.slotmind.scheduling;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class RuleBasedStrategy implements SchedulingStrategy {
    @Override
    public String name() {
        return "rule";
    }

    @Override
    public List<Recommendation> recommend(SchedulingContext c) {
        return c.candidates().stream().sorted(Comparator
                        .comparing((SchedulingContext.Candidate s) -> !s.providerId().equals(c.originalProviderId()))
                        .thenComparingLong(s -> Math.abs(Duration.between(c.originalStart(), s.startTime()).toSeconds()))
                        .thenComparing(SchedulingContext.Candidate::startTime)
                        .thenComparing(SchedulingContext.Candidate::slotId))
                .map(s -> {
                    boolean same = s.providerId().equals(c.originalProviderId());
                    String reason = same ? "Your doctor has an opening at " : s.providerName() + ", who has the same specialty, has an opening at ";
                    reason += s.startTime().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")) + ". Please confirm this time with the patient.";
                    return new Recommendation(s.slotId(), same ? 0.95 : 0.80, reason);
                }).toList();
    }
}
