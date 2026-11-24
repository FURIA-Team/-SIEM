package kz.minisiem.minisiem.repository;

import kz.minisiem.minisiem.entity.SecurityEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SecurityEventRepository extends JpaRepository<SecurityEvent, Long> {
    @Query("""
            select e from SecurityEvent e
            where (:severity is null or lower(e.severity) = lower(:severity))
              and (:query is null or lower(e.title) like lower(concat('%', :query, '%'))
                   or lower(e.source) like lower(concat('%', :query, '%'))
                   or lower(e.destination) like lower(concat('%', :query, '%'))
                   or lower(e.details) like lower(concat('%', :query, '%')))
              and (:since is null or e.occurredAt >= :since)
              and (:until is null or e.occurredAt <= :until)
            """)
    Page<SecurityEvent> search(
            @Param("query") String query,
            @Param("severity") String severity,
            @Param("since") LocalDateTime since,
            @Param("until") LocalDateTime until,
            Pageable pageable);

    @Query("""
            select e from SecurityEvent e
            where (:severity is null or lower(e.severity) = lower(:severity))
              and (:query is null or lower(e.title) like lower(concat('%', :query, '%'))
                   or lower(e.source) like lower(concat('%', :query, '%'))
                   or lower(e.destination) like lower(concat('%', :query, '%'))
                   or lower(e.details) like lower(concat('%', :query, '%')))
              and (:since is null or e.occurredAt >= :since)
              and (:until is null or e.occurredAt <= :until)
            order by e.occurredAt desc
            """)
    List<SecurityEvent> searchAll(
            @Param("query") String query,
            @Param("severity") String severity,
            @Param("since") LocalDateTime since,
            @Param("until") LocalDateTime until);

    long countByOccurredAtAfter(LocalDateTime time);
    long countByStatusIgnoreCase(String status);
    long countBySeverityIgnoreCaseAndOccurredAtAfter(String severity, LocalDateTime time);
    List<SecurityEvent> findAllByOccurredAtAfterOrderByOccurredAtAsc(LocalDateTime time);
}
