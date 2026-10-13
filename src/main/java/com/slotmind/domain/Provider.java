package com.slotmind.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "providers")
public class Provider {
    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Specialty specialty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProviderStatus status;

    @Version
    private long version;

    protected Provider() {}

    public Provider(String id, String name, Specialty specialty, ProviderStatus status) {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(name, "name is required");
        Objects.requireNonNull(specialty, "specialty is required");
        Objects.requireNonNull(status, "status is required");
        this.id = id;
        this.name = name;
        this.specialty = specialty;
        this.status = status;
    }

    public void changeStatus(ProviderStatus next) { status = Objects.requireNonNull(next); }

    public String getId() { return id; }
    public String getName() { return name; }
    public Specialty getSpecialty() { return specialty; }
    public ProviderStatus getStatus() { return status; }
    public long getVersion() { return version; }
}
