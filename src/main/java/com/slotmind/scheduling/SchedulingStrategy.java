package com.slotmind.scheduling;

import java.util.List;

public interface SchedulingStrategy {
    String name();

    List<Recommendation> recommend(SchedulingContext context);
}
