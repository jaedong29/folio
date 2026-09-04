package com.assetdashboard.evidence.agent;

import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LlmDailyUsageRepository extends JpaRepository<LlmDailyUsage, LocalDate> {

  /** 행이 이미 존재할 때만 원자적으로 더한다. 없으면 0을 반환해 호출자가 먼저 행을 만들게 한다. */
  @Modifying
  @Query(
      "update LlmDailyUsage u set u.callCount = u.callCount + 1, "
          + "u.inputTokens = u.inputTokens + :inputTokens, "
          + "u.outputTokens = u.outputTokens + :outputTokens "
          + "where u.usageDate = :usageDate")
  int increment(
      @Param("usageDate") LocalDate usageDate,
      @Param("inputTokens") long inputTokens,
      @Param("outputTokens") long outputTokens);
}
