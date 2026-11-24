package kz.minisiem.minisiem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_settings")
public class SystemSettings {
    @Id
    private Long id = 1L;

    @Column(nullable = false, length = 100)
    private String workspaceName = "MiniSIEM";

    @Column(nullable = false, length = 60)
    private String timezone = "UTC";

    @Column(nullable = false)
    private int retentionDays = 90;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public SystemSettings() {
    }

    @PrePersist
    @PreUpdate
    void updateTimestamp() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getWorkspaceName() { return workspaceName; }
    public void setWorkspaceName(String workspaceName) { this.workspaceName = workspaceName; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public int getRetentionDays() { return retentionDays; }
    public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
