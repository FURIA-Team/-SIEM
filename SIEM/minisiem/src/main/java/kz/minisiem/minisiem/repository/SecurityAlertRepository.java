package kz.minisiem.minisiem.repository;

import kz.minisiem.minisiem.entity.SecurityAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface SecurityAlertRepository extends JpaRepository<SecurityAlert, Long> {
    @Query("""
            select a from SecurityAlert a
            where (:query is null or lower(a.title) like lower(concat('%', :query, '%'))
                   or lower(a.source) like lower(concat('%', :query, '%'))
                   or lower(a.details) like lower(concat('%', :query, '%')))
              and (:severity is null or lower(a.severity) = lower(:severity))
              and (:status is null or lower(a.status) = lower(:status))
            """)
    Page<SecurityAlert> search(
            @Param("query") String query,
            @Param("severity") String severity,
            @Param("status") String status,
            Pageable pageable);

    long countBySeverityIgnoreCaseAndStatusNotIgnoreCase(String severity, String status);
    long countByStatusNotIgnoreCase(String status);

    List<SecurityAlert> findAllByStatusNotIgnoreCaseOrderByDetectedAtDesc(String status, Pageable pageable);
}
