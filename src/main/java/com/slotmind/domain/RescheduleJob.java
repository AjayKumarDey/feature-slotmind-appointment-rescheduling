package com.slotmind.domain;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity @Table(name = "reschedule_jobs")
public class RescheduleJob {
    @Id private String id;
    @Column(nullable = false) private String appointmentId;
    @Column(nullable = false) private String originalSlotId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TriggerReason triggerReason;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private JobStatus status;
    private int attempts;
    private LocalDateTime availableAt;
    private LocalDateTime startedAt;
    @Column(length = 500) private String message;
    @Version private long version;
    protected RescheduleJob() {}
    public RescheduleJob(String id, Appointment a, TriggerReason trigger, LocalDateTime now) {
        this.id=id; appointmentId=a.getId(); originalSlotId=a.getSlot().getId(); triggerReason=trigger;
        status=JobStatus.QUEUED; availableAt=now;
    }
    public void claim(LocalDateTime now) { status=JobStatus.RUNNING; startedAt=now; attempts++; }
    public void finish(JobStatus status, String message, LocalDateTime next) {
        this.status=status; this.message=message == null ? null : message.substring(0, Math.min(500,message.length())); availableAt=next;
    }
    public String getId(){return id;} public String getAppointmentId(){return appointmentId;}
    public String getOriginalSlotId(){return originalSlotId;} public TriggerReason getTriggerReason(){return triggerReason;}
    public JobStatus getStatus(){return status;} public int getAttempts(){return attempts;}
    public LocalDateTime getAvailableAt(){return availableAt;} public LocalDateTime getStartedAt(){return startedAt;}
    public String getMessage(){return message;}
}
