package kz.minisiem.minisiem.repository;

import kz.minisiem.minisiem.entity.SystemSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemSettingsRepository extends JpaRepository<SystemSettings, Long> {
}
