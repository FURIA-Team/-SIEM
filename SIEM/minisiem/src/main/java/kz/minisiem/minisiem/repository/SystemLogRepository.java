package kz.minisiem.minisiem.repository;

import kz.minisiem.minisiem.entity.SystemLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SystemLogRepository extends JpaRepository<SystemLog, Long> {
    @Query("""
            select l from SystemLog l
            where (:query is null or lower(l.level) like lower(concat('%', :query, '%'))
                   or lower(l.category) like lower(concat('%', :query, '%'))
                   or lower(l.message) like lower(concat('%', :query, '%'))
                   or lower(l.actor) like lower(concat('%', :query, '%')))
            """)
    Page<SystemLog> search(@Param("query") String query, Pageable pageable);
}
