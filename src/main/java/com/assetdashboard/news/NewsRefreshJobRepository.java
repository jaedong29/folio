package com.assetdashboard.news;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NewsRefreshJobRepository extends JpaRepository<NewsRefreshJob, Long> {

  Optional<NewsRefreshJob> findByJobId(String jobId);

  Optional<NewsRefreshJob> findFirstBySourceKeyAndStatusInOrderByCreatedAtDesc(
      String sourceKey, Collection<NewsRefreshStatus> statuses);

  Optional<NewsRefreshJob> findFirstBySourceKeyAndStatusOrderByCompletedAtDesc(
      String sourceKey, NewsRefreshStatus status);

  Optional<NewsRefreshJob> findFirstBySourceKeyOrderByCreatedAtDesc(String sourceKey);

  List<NewsRefreshJob> findAllByStatus(NewsRefreshStatus status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select j from NewsRefreshJob j where j.status = :status order by j.createdAt asc")
  List<NewsRefreshJob> findNextForUpdate(
      @Param("status") NewsRefreshStatus status, Pageable pageable);
}
