package com.slotmind.scheduling;

import com.slotmind.domain.*;
import com.slotmind.repository.SlotRepository;
import com.slotmind.config.SchedulingProperties;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SchedulingEngine {
    private final SlotRepository slots;
    private final Clock clock;
    private final Map<String, SchedulingStrategy> registry;
    private final SchedulingProperties properties;

    public SchedulingEngine(SlotRepository slots, Clock clock, List<SchedulingStrategy> strategies,
                            SchedulingProperties properties) {
        this.slots = slots;
        this.clock = clock;
        this.properties = properties;
        registry = strategies.stream()
                .collect(Collectors.toMap(SchedulingStrategy::name, Function.identity()));
        resolveStrategy();
    }

    public Set<String> strategyNames() {
        return new TreeSet<>(registry.keySet());
    }

    private SchedulingStrategy resolveStrategy() {
        var selected = properties.getStrategy();
        var strategy = registry.get(selected);
        if (strategy == null) throw new IllegalArgumentException("Unknown scheduling strategy: " + selected);
        return strategy;
    }

    public SchedulingContext contextFor(Appointment a, TriggerReason trigger) {
        var now = LocalDateTime.now(clock);
        var candidates = slots.findAll().stream()
                .filter(s -> s.getStatus() == SlotStatus.OPEN && s.getStartTime().isAfter(now))
                .filter(s -> s.getProvider().getStatus() != ProviderStatus.OFFLINE)
                .filter(s -> !s.getId().equals(a.getSlot().getId()))
                .filter(s -> s.getProvider().getSpecialty() == a.getProvider().getSpecialty())
                .filter(s -> s.getDurationMinutes() >= a.getSlot().getDurationMinutes())
                .map(s -> new SchedulingContext.Candidate(s.getId(), s.getProvider().getId(),
                        s.getProvider().getName(), s.getStartTime(), s.getDurationMinutes())).toList();
        return new SchedulingContext(a.getId(), a.getAppointmentType(),
                a.getProvider().getId(), a.getProvider().getSpecialty(), a.getSlot().getStartTime(),
                a.getSlot().getDurationMinutes(), trigger, candidates);
    }

    public List<Recommendation> recommend(SchedulingContext context) {
        if (context.candidates().isEmpty()) return List.of();
        return resolveStrategy().recommend(context);
    }

    public List<Recommendation> recommend(Appointment a, TriggerReason trigger) {
        return recommend(contextFor(a, trigger));
    }
}
