package com.slotmind.controller;
import com.slotmind.config.SchedulingProperties;
import com.slotmind.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import com.slotmind.scheduling.SchedulingEngine;
@RestController
@RequestMapping("/scheduling/strategy")
public class StrategyController {
    public record Update(@NotBlank String strategy) {}
    private final SchedulingProperties config;
    private final SchedulingEngine engine;
    public StrategyController(SchedulingProperties config,SchedulingEngine engine){this.config=config;this.engine=engine;}
    @GetMapping public Map<String,Object> current(){return Map.of("strategy",config.getStrategy(),"availableStrategies",engine.strategyNames());}
    @PatchMapping public Map<String,Object> change(@Valid @RequestBody Update r){
        if(!engine.strategyNames().contains(r.strategy()))throw new ApiException(HttpStatus.BAD_REQUEST,"Unknown scheduling strategy");
        config.setStrategy(r.strategy());return current();
    }
}
