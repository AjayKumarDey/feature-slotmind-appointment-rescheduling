package com.slotmind;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication
public class SlotMindApplication {
    public static void main(String[] args) {
        SpringApplication.run(SlotMindApplication.class, args);
    }
}
