package com.slotmind;
import com.slotmind.ai.*;
import com.slotmind.domain.*;
import com.slotmind.scheduling.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class AiStrategyTest {
    SchedulingContext context(TriggerReason trigger) {
        var now=LocalDateTime.of(2026,10,13,10,0);
        return new SchedulingContext("APT-X",AppointmentType.URGENT,"P1",Specialty.GENERAL,now,30,trigger,
            List.of(new SchedulingContext.Candidate("S2","P2","Dr. Rahul",now.plusMinutes(30),30)));
    }
    AiSchedulingStrategy strategy(LlmGateway g){return new AiSchedulingStrategy(g,new ObjectMapper().findAndRegisterModules(),new RuleBasedStrategy());}
    @Test void validAiRecommendation(){
        var s=strategy(p->"{\"slotId\":\"S2\",\"alternateProviderId\":\"P2\",\"confidence\":0.88,\"reasoning\":\"Dr. Rahul can see you at 10:30.\"}");
        var result=s.recommend(context(TriggerReason.PROVIDER_OFFLINE)).get(0);
        assertEquals("AI",result.source());assertEquals(0.88,result.confidence());
    }
    @Test void invalidOutputsAlwaysFallBack(){
        for(String body:List.of("not json","{}",
            "{\"slotId\":\"invented\",\"alternateProviderId\":\"P2\",\"confidence\":0.8,\"reasoning\":\"Opening\"}",
            "{\"slotId\":\"S2\",\"alternateProviderId\":\"wrong\",\"confidence\":0.8,\"reasoning\":\"Opening\"}",
            "{\"slotId\":\"S2\",\"alternateProviderId\":\"P2\",\"confidence\":2,\"reasoning\":\"Opening\"}",
            "{\"slotId\":\"S2\",\"alternateProviderId\":\"P2\",\"confidence\":\"0.8\",\"reasoning\":\"Opening\"}",
            "{\"slotId\":\"S2\",\"alternateProviderId\":\"P2\",\"confidence\":0.8,\"reasoning\":\"\"}")){
            var result=strategy(p->body).recommend(context(TriggerReason.INITIAL)).get(0);
            assertEquals("RULE_FALLBACK",result.source());assertEquals("S2",result.slotId());
        }
    }
    @Test void gatewayFailureFallsBack(){assertEquals("RULE_FALLBACK",strategy(p->{throw new java.io.IOException("timeout");}).recommend(context(TriggerReason.INITIAL)).get(0).source());}
    @Test void triggerPromptsDifferAndContainUrgency() throws Exception {
        var s=strategy(p->"");var offline=s.prompt(context(TriggerReason.PROVIDER_OFFLINE));var cancel=s.prompt(context(TriggerReason.SLOT_CANCELLED));
        assertTrue(offline.contains("original doctor is OFFLINE"));assertTrue(cancel.contains("time slot was CANCELLED"));
        assertTrue(offline.contains("URGENT"));assertNotEquals(offline,cancel);
    }
    @Test void noCandidatesDoesNotCallLlm(){
        var c=context(TriggerReason.INITIAL);
        var empty=new SchedulingContext(c.appointmentId(),c.appointmentType(),c.originalProviderId(),c.specialty(),c.originalStart(),30,c.triggerReason(),List.of());
        assertTrue(strategy(p->{fail("LLM should not be called");return "";}).recommend(empty).isEmpty());
    }
}
