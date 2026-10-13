package com.slotmind;
import com.slotmind.config.SeedData;
import com.slotmind.domain.*;
import com.slotmind.dto.*;
import com.slotmind.exception.ApiException;
import com.slotmind.repository.*;
import com.slotmind.service.*;
import com.slotmind.scheduling.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties = {"worker.enabled=false", "scheduling.strategy=rule"}) @AutoConfigureMockMvc
class RescheduleIntegrationTest {
    @Autowired RescheduleService reschedule;
    @Autowired AppointmentService booking;
    @Autowired ClinicQueryService queries;
    @Autowired AppointmentRepository appointments;
    @Autowired ProviderRepository providers;
    @Autowired SlotRepository slots;
    @Autowired RescheduleSuggestionRepository suggestions;
    @Autowired SeedData seed;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @BeforeEach void reset() {
        suggestions.deleteAll(); appointments.deleteAll(); slots.deleteAll(); providers.deleteAll(); seed.run();
    }
    private SuggestionResponse suggest(String id) { return reschedule.suggest(id, TriggerReason.INITIAL); }
    private AppointmentResponse appointment(String id) {
        return queries.appointments(null).stream().filter(a -> a.id().equals(id)).findFirst().orElseThrow();
    }
    private void accept(String id) { reschedule.decide(id, DecideSuggestionRequest.Decision.ACCEPTED); }
    @Test void createsManualSuggestionAndDeduplicates() {
        var s = suggest("APT-001");
        assertEquals("SLT-005", s.recommendedSlotId()); assertEquals("PRV-004", s.alternateProviderId());
        assertEquals(TriggerReason.INITIAL, s.triggerReason());
        assertEquals(AppointmentStatus.RESCHEDULE_PENDING, appointment("APT-001").status());
        assertEquals(s.id(), suggest("APT-001").id()); assertEquals(1, suggestions.count());
        assertEquals(SlotStatus.OPEN, slots.findById("SLT-005").orElseThrow().getStatus());
    }
    @Test void acceptsAtomicallyAndCannotDecideTwice() {
        var s = suggest("APT-001"); accept(s.id());
        var a = appointment("APT-001");
        assertEquals("SLT-005", a.slotId()); assertEquals("PRV-004", a.providerId());
        assertEquals(AppointmentStatus.RESCHEDULED, a.status());
        assertEquals(SlotStatus.OPEN, slots.findById("SLT-001").orElseThrow().getStatus());
        assertEquals(SlotStatus.BOOKED, slots.findById("SLT-005").orElseThrow().getStatus());
        assertEquals(SuggestionStatus.ACCEPTED, suggestions.findById(s.id()).orElseThrow().getStatus());
        assertEquals(409, assertThrows(ApiException.class, () -> accept(s.id())).getStatus().value());
    }
    @Test void rejectKeepsOriginalBooking() {
        var s = suggest("APT-001"); reschedule.decide(s.id(), DecideSuggestionRequest.Decision.REJECTED);
        assertEquals(AppointmentStatus.CONFIRMED, appointment("APT-001").status());
        assertEquals("SLT-001", appointment("APT-001").slotId());
        assertEquals(SlotStatus.BOOKED, slots.findById("SLT-001").orElseThrow().getStatus());
        assertEquals(SlotStatus.OPEN, slots.findById("SLT-005").orElseThrow().getStatus());
        assertEquals(SuggestionStatus.REJECTED, suggestions.findById(s.id()).orElseThrow().getStatus());
        assertNotEquals(s.id(), suggest("APT-001").id());
    }
    @Test void noCompatibleSlotLeavesStateUnchanged() {
        var e = assertThrows(ApiException.class, () -> suggest("APT-003"));
        assertEquals(409, e.getStatus().value());
        assertEquals(AppointmentStatus.CONFIRMED, appointment("APT-003").status());
        assertEquals(0, suggestions.count());
    }
    @Test void prefersSameProviderBeforeCloserAlternativeAndThenNearest() {
        var p = providers.findById("PRV-001").orElseThrow();
        var original = slots.findById("SLT-001").orElseThrow().getStartTime();
        slots.save(new Slot("SAME-FAR", p, original.plusHours(4), 30, SlotStatus.OPEN));
        slots.save(new Slot("SAME-NEAR", p, original.plusHours(3), 30, SlotStatus.OPEN));
        assertEquals("SAME-NEAR", suggest("APT-001").recommendedSlotId());
    }
    @Test void excludesPastShortOfflineAndWrongSpecialty() {
        var p = providers.findById("PRV-001").orElseThrow();
        var time = LocalDateTime.now();
        slots.save(new Slot("SHORT", p, time.plusMinutes(70), 10, SlotStatus.OPEN));
        slots.save(new Slot("PAST", p, time.minusMinutes(1), 30, SlotStatus.OPEN));
        var offline = providers.save(new Provider("OFF", "Offline", Specialty.GENERAL, ProviderStatus.OFFLINE));
        slots.save(new Slot("OFF-SLOT", offline, time.plusMinutes(61), 30, SlotStatus.OPEN));
        var wrong = providers.findById("PRV-002").orElseThrow();
        slots.save(new Slot("WRONG", wrong, time.plusMinutes(61), 30, SlotStatus.OPEN));
        assertEquals("SLT-005", suggest("APT-001").recommendedSlotId());
    }
    @Test void bookedRecommendationFailsWithoutChangingOldBooking() {
        var s = suggest("APT-001");
        booking.book(new BookAppointmentRequest("Other patient", AppointmentType.ROUTINE, "PRV-004", "SLT-005"));
        assertThrows(ApiException.class, () -> accept(s.id()));
        assertEquals("SLT-001", appointment("APT-001").slotId());
        assertEquals(SlotStatus.BOOKED, slots.findById("SLT-001").orElseThrow().getStatus());
        assertEquals(SuggestionStatus.PENDING, suggestions.findById(s.id()).orElseThrow().getStatus());
    }
    @Test void cancelledOldSlotStaysCancelledAndRejectOfDisruptionStaysPending() {
        jdbc.update("update slots set status = 'CANCELLED' where id = 'SLT-001'");
        var s = suggest("APT-001"); reschedule.decide(s.id(), DecideSuggestionRequest.Decision.REJECTED);
        assertEquals(AppointmentStatus.RESCHEDULE_PENDING, appointment("APT-001").status());
        accept(suggest("APT-001").id());
        assertEquals(SlotStatus.CANCELLED, slots.findById("SLT-001").orElseThrow().getStatus());
    }
    @Test void providerGoingOfflineAfterSuggestionPreventsAccept() {
        var s = suggest("APT-001");
        jdbc.update("update providers set status = 'OFFLINE' where id = 'PRV-004'");
        assertThrows(ApiException.class, () -> accept(s.id()));
        assertEquals(SlotStatus.OPEN, slots.findById("SLT-005").orElseThrow().getStatus());
        assertEquals("SLT-001", appointment("APT-001").slotId());
    }
    @Test void rollbackRestoresAllThreeRecords() {
        var s = suggest("APT-001");
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions).execute(status -> {
            accept(s.id()); throw new IllegalStateException("Failure after accept");
        }));
        assertEquals("SLT-001", appointment("APT-001").slotId());
        assertEquals(AppointmentStatus.RESCHEDULE_PENDING, appointment("APT-001").status());
        assertEquals(SlotStatus.BOOKED, slots.findById("SLT-001").orElseThrow().getStatus());
        assertEquals(SlotStatus.OPEN, slots.findById("SLT-005").orElseThrow().getStatus());
        assertEquals(SuggestionStatus.PENDING, suggestions.findById(s.id()).orElseThrow().getStatus());
    }
    @Test void twoAcceptsForSameSlotOnlyOneSucceeds() throws Exception {
        var first = suggest("APT-001"); var second = suggest("APT-002");
        var executor = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            var f1 = executor.submit(() -> attempt(first.id(), start));
            var f2 = executor.submit(() -> attempt(second.id(), start)); start.countDown();
            assertEquals(1, (f1.get(10, TimeUnit.SECONDS) ? 1 : 0) + (f2.get(10, TimeUnit.SECONDS) ? 1 : 0));
            assertEquals(1, queries.appointments(null).stream().filter(a -> a.slotId().equals("SLT-005")).count());
            assertEquals(1, queries.appointments(AppointmentStatus.RESCHEDULED).size());
            assertEquals(1, queries.appointments(AppointmentStatus.RESCHEDULE_PENDING).size());
        } finally { executor.shutdownNow(); }
    }
    private boolean attempt(String id, CountDownLatch start) throws Exception {
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
        try { accept(id); return true; }
        catch (ApiException e) { assertEquals(409, e.getStatus().value()); return false; }
    }
    @Test void httpEndpointsAndInvalidDecision() throws Exception {
        mvc.perform(post("/appointments/APT-001/suggest")).andExpect(status().isOk())
            .andExpect(jsonPath("recommendedSlotId").value("SLT-005"));
        var s = suggest("APT-001");
        mvc.perform(patch("/suggestions/" + s.id()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PENDING\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(patch("/suggestions/" + s.id()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACCEPTED\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("status").value("ACCEPTED"));
        mvc.perform(post("/appointments/missing/suggest")).andExpect(status().isNotFound());
        mvc.perform(patch("/suggestions/missing").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REJECTED\"}"))
            .andExpect(status().isNotFound());
    }
    @Test void alternatePendingSuggestionsBecomeObsoleteAfterAccept() {
        var first = suggest("APT-001");
        var other = reschedule.suggest("APT-001", TriggerReason.SLOT_CANCELLED);
        accept(first.id());
        assertEquals(SuggestionStatus.REJECTED, suggestions.findById(other.id()).orElseThrow().getStatus());
        assertThrows(ApiException.class, () -> accept(other.id()));
    }
    @Test void switchesBetweenRegisteredStrategiesWithoutChangingService() {
        var config = new com.slotmind.config.SchedulingProperties();
        SchedulingStrategy custom = new SchedulingStrategy() {
            public String name() { return "custom"; }
            public java.util.List<Recommendation> recommend(SchedulingContext context) {
                return java.util.List.of(new Recommendation(context.candidates().get(0).slotId(), 0.75, "Custom recommendation"));
            }
        };
        var engine = new SchedulingEngine(slots, java.time.Clock.systemDefaultZone(),
            java.util.List.of(new RuleBasedStrategy(), custom), config);
        var a = appointments.findById("APT-001").orElseThrow();
        assertFalse(engine.recommend(a, TriggerReason.INITIAL).get(0).reasoning().contains("Custom"));
        config.setStrategy("custom");
        assertEquals("Custom recommendation", engine.recommend(a, TriggerReason.INITIAL).get(0).reasoning());
    }
    @Test void rejectingAfterSecondRescheduleRestoresRescheduledStatus() {
        accept(suggest("APT-001").id());
        var s = suggest("APT-001");
        reschedule.decide(s.id(), DecideSuggestionRequest.Decision.REJECTED);
        assertEquals(AppointmentStatus.RESCHEDULED, appointment("APT-001").status());
    }
}
