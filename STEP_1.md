# Step 1 — Project setup and domain model

## 1. Open the project
Open pom.xml in IntelliJ, select JDK 17 or 21, and let Maven download dependencies.
The build uses Spring Boot 3.5.5, a fixed 3.x version for this learning project.

## 2. Understand the packages
- domain: database entities and enums.
- repository: JPA interfaces that save/read entities.
- config: startup seed data.
- controller: HTTP read endpoints.

Later we will add dto, service, scheduling, ai, and event packages.

## 3. Understand the four classes
Provider represents a doctor, with specialty and status.
Slot represents a doctor's start time and duration.
Appointment connects a patient to a doctor and a slot.
RescheduleSuggestion proposes moving an appointment to another slot.

## 4. Understand the annotations
@Entity: JPA stores this class in the database.
@Table: chooses the table name.
@Id: primary key.
@Enumerated(EnumType.STRING): stores status as readable text, such as CONFIRMED.
@ManyToOne: many records can refer to one doctor or appointment.
@OneToOne: one current appointment per slot in this sprint.
@JoinColumn: identifies the foreign key column.
@Version: helps detect concurrent changes; complete transaction handling comes later.

A unique slot_id constraint prevents two current appointment rows using the same slot.
This alone does not implement booking or atomic rescheduling; Step 2 adds service rules.

## 5. Run
Run SlotMindApplication from IntelliJ or run mvn spring-boot:run.
The application starts on port 8080.

## 6. Check in Postman
GET http://localhost:8080/providers -> five doctors.
GET http://localhost:8080/slots -> six time slots.
GET http://localhost:8080/appointments?status=CONFIRMED -> four appointments.
GET http://localhost:8080/suggestions -> empty list, since scheduling comes later.

## 7. Next checkpoint
Add BookAppointmentRequest, response DTOs, AppointmentService, POST /appointments,
validated state transitions, and conflict handling. Keep transactions in the service.
