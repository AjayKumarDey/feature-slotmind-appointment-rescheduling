package com.slotmind.controller;
import com.slotmind.dto.*;
import com.slotmind.service.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
public class DisruptionController {
    private final DisruptionService disruptions;private final JobTransactions jobs;
    public DisruptionController(DisruptionService disruptions,JobTransactions jobs){this.disruptions=disruptions;this.jobs=jobs;}
    @PatchMapping("/providers/{id}/status") public ProviderResponse provider(@PathVariable String id,@Valid @RequestBody StatusRequests.ProviderUpdate r){return disruptions.providerStatus(id,r.status());}
    @PatchMapping("/slots/{id}/status") public SlotResponse slot(@PathVariable String id,@Valid @RequestBody StatusRequests.SlotUpdate r){return disruptions.slotStatus(id,r.status());}
    @GetMapping("/reschedule-jobs") public List<JobResponse> jobs(){return jobs.list();}
}
