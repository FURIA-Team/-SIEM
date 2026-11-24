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
@Table(name = "security_alerts")
public class SecurityAlert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", unique = true)
    private Long eventId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 16)
    private String severity;

    @Column(nullable = false, length = 100)
    private String source;

    @Column(nullable = false, length = 20)
    private String status = "OPEN";

    @Column(nullable = false)
    private LocalDateTime detectedAt;

    @Column(length = 2000)
    private String details;

    public SecurityAlert() {
    }

    @PrePersist
    void setDefaults() {
        if (detectedAt == null) detectedAt = LocalDateTime.now();
        if (status == null) status = "OPEN";
    }

    public Long getId() { return id; }
    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getDetectedAt() { return detectedAt; }
    public void setDetectedAt(LocalDateTime detectedAt) { this.detectedAt = detectedAt; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
