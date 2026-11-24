package kz.minisiem.minisiem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "security_events")
public class SecurityEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 16)
    private String severity;

    @Column(nullable = false, length = 100)
    private String source;

    @Column(nullable = false, length = 200)
    private String destination;

    @Column(nullable = false, length = 24)
    private String status = "OPEN";

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @Column(length = 2000)
    private String details;

    @Column(nullable = false, length = 50)
    private String submittedBy;

    public SecurityEvent() {
    }

    @PrePersist
    void setDefaults() {
        if (occurredAt == null) occurredAt = LocalDateTime.now();
        if (status == null) status = "OPEN";
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }
}
