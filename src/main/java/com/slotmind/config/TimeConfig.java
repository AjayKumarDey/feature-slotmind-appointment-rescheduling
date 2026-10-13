package com.slotmind.config;
import org.springframework.context.annotation.*;
import java.time.Clock;
@Configuration
public class TimeConfig {
    @Bean public Clock clinicClock() { return Clock.systemDefaultZone(); }
}
