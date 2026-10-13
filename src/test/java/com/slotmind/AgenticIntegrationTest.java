package com.slotmind;
import com.slotmind.config.SeedData;
import com.slotmind.domain.*;
import com.slotmind.dto.*;
import com.slotmind.repository.*;
import com.slotmind.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"worker.enabled=false","scheduling.strategy=rule"}) @AutoConfigureMockMvc
class AgenticIntegrationTest {
    @Autowired DisruptionService disruptions;@Autowired RescheduleService reschedule;
    @Autowired RescheduleJobRepository jobs;@Autowired JobTransactions jobTransactions;
    @Autowired AppointmentRepository appointments;@Autowired SlotRepository slots;@Autowired ProviderRepository providers;
    @Autowired RescheduleSuggestionRepository suggestions;@Autowired SeedData seed;@Autowired MockMvc mvc;
    @Autowired com.slotmind.config.SchedulingProperties config;
    @Autowired PlatformTransactionManager transactions;@Autowired CompletionService completion;
    @BeforeEach void reset(){jobs.deleteAll();suggestions.deleteAll();appointments.deleteAll();slots.deleteAll();providers.deleteAll();seed.run();}
    void work(){new RescheduleWorker(jobTransactions,reschedule).tick();}
    @Test void offlineQueuesTwoThenGeneratesWithoutManualRequest(){
        disruptions.providerStatus("PRV-001",ProviderStatus.OFFLINE);
        assertEquals(2,jobs.count());assertEquals(0,suggestions.count());
        assertEquals(AppointmentStatus.RESCHEDULE_PENDING,appointments.findById("APT-001").orElseThrow().getStatus());
        work();assertEquals(2,suggestions.count());
        assertTrue(suggestions.findAll().stream().allMatch(s->s.getTriggerReason()==TriggerReason.PROVIDER_OFFLINE));
        assertTrue(jobs.findAll().stream().allMatch(j->j.getStatus()==JobStatus.DONE));
        disruptions.providerStatus("PRV-001",ProviderStatus.OFFLINE);work();assertEquals(2,suggestions.count());assertEquals(2,jobs.count());
    }
    @Test void cancelTriggersOneAndAcceptKeepsCancelledOldSlot(){
        disruptions.slotStatus("SLT-001",SlotStatus.CANCELLED);assertEquals(1,jobs.count());work();
        var s=suggestions.findAll().get(0);assertEquals(TriggerReason.SLOT_CANCELLED,s.getTriggerReason());
        reschedule.decide(s.getId(),DecideSuggestionRequest.Decision.ACCEPTED);
        assertEquals(SlotStatus.CANCELLED,slots.findById("SLT-001").orElseThrow().getStatus());
        assertEquals(AppointmentStatus.RESCHEDULED,appointments.findById("APT-001").orElseThrow().getStatus());
    }
    @Test void noOptionsIsVisibleAndAppointmentStaysPending(){
        disruptions.providerStatus("PRV-003",ProviderStatus.OFFLINE);work();
        assertEquals(JobStatus.NO_OPTIONS,jobs.findAll().get(0).getStatus());
        assertEquals(AppointmentStatus.RESCHEDULE_PENDING,appointments.findById("APT-003").orElseThrow().getStatus());
        assertEquals(0,suggestions.count());
    }
    @Test void statusAndQueuedJobsRollBackTogether(){
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(transactions).execute(t->{
            disruptions.providerStatus("PRV-001",ProviderStatus.OFFLINE);throw new IllegalStateException("Simulated commit failure");
        }));assertEquals(0,jobs.count());assertEquals(ProviderStatus.BUSY,providers.findById("PRV-001").orElseThrow().getStatus());
        assertEquals(AppointmentStatus.CONFIRMED,appointments.findById("APT-001").orElseThrow().getStatus());
    }
    @Test void completedAppointmentsAreSkipped(){
        completion.complete("APT-001");disruptions.providerStatus("PRV-001",ProviderStatus.OFFLINE);
        assertEquals(1,jobs.count());assertEquals("APT-002",jobs.findAll().get(0).getAppointmentId());
    }
    @Test void duplicateJobClaimIsSkipped(){
        disruptions.slotStatus("SLT-001",SlotStatus.CANCELLED);var id=jobs.findAll().get(0).getId();
        var first=jobTransactions.claim(id);assertNotNull(first);assertNull(jobTransactions.claim(id));
        jobTransactions.finish(first,JobStatus.DONE,"Test completed");
    }
    @Test void aiFailureFallsBackInAutomaticFlow(){
        config.setStrategy("ai");
        try{
            disruptions.providerStatus("PRV-001",ProviderStatus.OFFLINE);work();
            assertEquals(2,suggestions.count());
            assertTrue(suggestions.findAll().stream().allMatch(s->s.getStrategySource().equals("RULE_FALLBACK")));
        }finally{config.setStrategy("rule");}
    }
    @Test void runtimeStrategyEndpointValidatesRegistry() throws Exception {
        try{
            mvc.perform(patch("/scheduling/strategy").contentType(MediaType.APPLICATION_JSON).content("{\"strategy\":\"ai\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("strategy").value("ai"));
            mvc.perform(patch("/scheduling/strategy").contentType(MediaType.APPLICATION_JSON).content("{\"strategy\":\"unknown\"}"))
                .andExpect(status().isBadRequest());
        }finally{config.setStrategy("rule");}
    }
    @Test void statusApisValidateInputAndProtectBookings() throws Exception {
        mvc.perform(patch("/providers/PRV-001/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OFFLINE\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("status").value("OFFLINE"));
        mvc.perform(get("/reschedule-jobs")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(patch("/slots/SLT-001/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OPEN\"}"))
            .andExpect(status().isConflict());
        mvc.perform(patch("/slots/SLT-005/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"BOOKED\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(patch("/providers/missing/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OFFLINE\"}"))
            .andExpect(status().isNotFound());
    }
}
