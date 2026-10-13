package com.slotmind;

import com.slotmind.config.SeedData;
import com.slotmind.domain.*;
import com.slotmind.dto.*;
import com.slotmind.exception.ApiException;
import com.slotmind.repository.*;
import com.slotmind.service.AppointmentService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"worker.enabled=false", "scheduling.strategy=rule"})
@AutoConfigureMockMvc
class BookingIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired AppointmentService service;
    @Autowired AppointmentRepository appointments;
    @Autowired SlotRepository slots;
    @Autowired ProviderRepository providers;
    @Autowired RescheduleSuggestionRepository suggestions;
    @Autowired SeedData seed;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach void reset() {
        suggestions.deleteAll(); appointments.deleteAll(); slots.deleteAll(); providers.deleteAll();
        seed.run();
    }
    private BookAppointmentRequest request(String name) {
        return new BookAppointmentRequest(name, AppointmentType.ROUTINE, "PRV-004", "SLT-005");
    }
    private String json(String name, String type, String provider, String slot) {
        return "{\"patientName\":\"" + name + "\",\"appointmentType\":\"" + type
            + "\",\"providerId\":\"" + provider + "\",\"slotId\":\"" + slot + "\"}";
    }
    private org.springframework.test.web.servlet.ResultActions postBooking(String body) throws Exception {
        return mvc.perform(post("/appointments").contentType(MediaType.APPLICATION_JSON).content(body));
    }
    @Test void booksOpenSlotAndReturnsDto() throws Exception {
        postBooking(json(" Ajay Dey ", "ROUTINE", "PRV-004", "SLT-005"))
            .andExpect(status().isCreated()).andExpect(jsonPath("patientName").value("Ajay Dey"))
            .andExpect(jsonPath("status").value("CONFIRMED"))
            .andExpect(jsonPath("providerId").value("PRV-004"))
            .andExpect(jsonPath("slotId").value("SLT-005"));
        assertEquals(SlotStatus.BOOKED, slots.findById("SLT-005").orElseThrow().getStatus());
        assertEquals(5, appointments.count());
        mvc.perform(get("/appointments").param("status", "CONFIRMED"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(5));
    }
    @Test void rejectsAlreadyBookedSlot() throws Exception {
        postBooking(json("Ajay", "ROUTINE", "PRV-001", "SLT-001"))
            .andExpect(status().isConflict());
        assertEquals(4, appointments.count());
    }
    @Test void validatesNameAndRequiredFields() throws Exception {
        postBooking(json(" ", "ROUTINE", "PRV-004", "SLT-005"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("errors").isNotEmpty());
        postBooking("{}").andExpect(status().isBadRequest());
        postBooking(json("x".repeat(121), "ROUTINE", "PRV-004", "SLT-005"))
            .andExpect(status().isBadRequest());
    }
    @Test void rejectsInvalidEnumAndMalformedJson() throws Exception {
        postBooking(json("Ajay", "INVALID", "PRV-004", "SLT-005"))
            .andExpect(status().isBadRequest());
        postBooking("{").andExpect(status().isBadRequest());
        mvc.perform(get("/appointments").param("status", "INVALID")).andExpect(status().isBadRequest());
    }
    @Test void missingProviderOrSlotReturns404() throws Exception {
        postBooking(json("Ajay", "ROUTINE", "missing", "SLT-005")).andExpect(status().isNotFound());
        postBooking(json("Ajay", "ROUTINE", "PRV-004", "missing")).andExpect(status().isNotFound());
    }
    @Test void mismatchedProviderLeavesSlotOpen() throws Exception {
        postBooking(json("Ajay", "ROUTINE", "PRV-002", "SLT-005"))
            .andExpect(status().isBadRequest());
        assertEquals(SlotStatus.OPEN, slots.findById("SLT-005").orElseThrow().getStatus());
    }
    @Test void rejectsOfflinePastAndCancelledSlots() throws Exception {
        var offline = providers.save(new Provider("PRV-OFF", "Offline doctor", Specialty.GENERAL, ProviderStatus.OFFLINE));
        var available = providers.findById("PRV-004").orElseThrow();
        slots.save(new Slot("SLT-OFF", offline, LocalDateTime.now().plusHours(1), 30, SlotStatus.OPEN));
        slots.save(new Slot("SLT-PAST", available, LocalDateTime.now().minusHours(1), 30, SlotStatus.OPEN));
        slots.save(new Slot("SLT-CANCEL", available, LocalDateTime.now().plusHours(1), 30, SlotStatus.CANCELLED));
        postBooking(json("Ajay", "ROUTINE", "PRV-OFF", "SLT-OFF")).andExpect(status().isConflict());
        postBooking(json("Ajay", "ROUTINE", "PRV-004", "SLT-PAST")).andExpect(status().isConflict());
        postBooking(json("Ajay", "ROUTINE", "PRV-004", "SLT-CANCEL")).andExpect(status().isConflict());
        assertEquals(4, appointments.count());
    }
    @Test void busyProviderCanStillBookOpenFutureSlot() throws Exception {
        var busy = providers.findById("PRV-001").orElseThrow();
        slots.save(new Slot("SLT-FUTURE", busy, LocalDateTime.now().plusHours(3), 30, SlotStatus.OPEN));
        postBooking(json("Ajay", "ROUTINE", "PRV-001", "SLT-FUTURE"))
            .andExpect(status().isCreated());
    }
    @Test void transactionRollbackRestoresSlotAndAppointment() {
        var tx = new TransactionTemplate(transactions);
        assertThrows(IllegalStateException.class, () -> tx.execute(status -> {
            service.book(request("Ajay"));
            throw new IllegalStateException("Simulated failure after booking");
        }));
        assertEquals(4, appointments.count());
        assertEquals(SlotStatus.OPEN, slots.findById("SLT-005").orElseThrow().getStatus());
    }
    @Test void simultaneousBookingsOnlyOneSucceeds() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            Callable<Boolean> task = () -> {
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                try { service.book(request("Patient " + Thread.currentThread().getId())); return true; }
                catch (ApiException e) { assertEquals(409, e.getStatus().value()); return false; }
            };
            var first = executor.submit(task); var second = executor.submit(task); start.countDown();
            int successes = (first.get(10, TimeUnit.SECONDS) ? 1 : 0) + (second.get(10, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successes); assertEquals(5, appointments.count());
            assertEquals(SlotStatus.BOOKED, slots.findById("SLT-005").orElseThrow().getStatus());
        } finally { executor.shutdownNow(); }
    }
}
