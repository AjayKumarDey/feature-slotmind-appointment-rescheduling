package com.slotmind.config;
import com.slotmind.domain.*;
import com.slotmind.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
@Component
public class SeedData implements CommandLineRunner {
    private final ProviderRepository providers;
    private final SlotRepository slots;
    private final AppointmentRepository appointments;
    public SeedData(ProviderRepository providers, SlotRepository slots, AppointmentRepository appointments) {
        this.providers = providers; this.slots = slots; this.appointments = appointments;
    }
    @Override @Transactional
    public void run(String... args) {
        if (providers.count() > 0) return;
        var p1 = new Provider("PRV-001", "Dr. Sarah Chen", Specialty.GENERAL, ProviderStatus.BUSY);
        var p2 = new Provider("PRV-002", "Dr. James Okonkwo", Specialty.SPECIALIST, ProviderStatus.AVAILABLE);
        var p3 = new Provider("PRV-003", "Dr. Priya Sharma", Specialty.PEDIATRIC, ProviderStatus.BUSY);
        var p4 = new Provider("PRV-004", "Dr. Rahul Verma", Specialty.GENERAL, ProviderStatus.AVAILABLE);
        var p5 = new Provider("PRV-005", "Dr. Ananya Iyer", Specialty.SPECIALIST, ProviderStatus.BUSY);
        providers.saveAll(List.of(p1,p2,p3,p4,p5));
        var now = LocalDateTime.now().withSecond(0).withNano(0);
        var s1 = new Slot("SLT-001", p1, now.plusMinutes(60), 30, SlotStatus.BOOKED);
        var s2 = new Slot("SLT-002", p1, now.plusMinutes(120), 30, SlotStatus.BOOKED);
        var s3 = new Slot("SLT-003", p3, now.plusMinutes(90), 45, SlotStatus.BOOKED);
        var s4 = new Slot("SLT-004", p2, now.plusMinutes(180), 30, SlotStatus.OPEN);
        var s5 = new Slot("SLT-005", p4, now.plusMinutes(150), 30, SlotStatus.OPEN);
        var s6 = new Slot("SLT-006", p5, now.plusMinutes(240), 60, SlotStatus.BOOKED);
        slots.saveAll(List.of(s1,s2,s3,s4,s5,s6));
        appointments.saveAll(List.of(
            new Appointment("APT-001", "John Martinez", AppointmentType.ROUTINE, p1, s1, AppointmentStatus.CONFIRMED),
            new Appointment("APT-002", "Emily Watson", AppointmentType.FOLLOW_UP, p1, s2, AppointmentStatus.CONFIRMED),
            new Appointment("APT-003", "Aisha Patel", AppointmentType.URGENT, p3, s3, AppointmentStatus.CONFIRMED),
            new Appointment("APT-004", "Robert Kim", AppointmentType.ROUTINE, p5, s6, AppointmentStatus.CONFIRMED)));
    }
}
