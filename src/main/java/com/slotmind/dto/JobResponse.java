package com.slotmind.dto;
import com.slotmind.domain.*;
public record JobResponse(String id,String appointmentId,TriggerReason triggerReason,JobStatus status,int attempts,String message) {
    public static JobResponse from(RescheduleJob j){return new JobResponse(j.getId(),j.getAppointmentId(),j.getTriggerReason(),j.getStatus(),j.getAttempts(),j.getMessage());}
}
