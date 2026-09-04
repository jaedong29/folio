package com.assetdashboard.news;

import com.assetdashboard.global.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "News", description = "공용 금융 뉴스와 공식자료 피드")
@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
public class NewsController {

  private final NewsQueryService queryService;
  private final NewsRefreshQueueService queueService;
  private final NewsRefreshJobLifecycleService lifecycleService;
  private final NewsSourceStatusService sourceStatusService;

  @Operation(summary = "공용 또는 내 자산 관련 뉴스 조회")
  @GetMapping
  public ResponseEntity<NewsFeedResponse> getFeed(
      @CurrentUserId Long userId,
      @RequestParam(required = false) NewsScope scope,
      @RequestParam(required = false) NewsCategory category,
      @RequestParam(required = false) String query,
      @RequestParam(required = false) Integer limit) {
    return ResponseEntity.ok(queryService.getFeed(userId, scope, category, query, limit));
  }

  @Operation(summary = "공식 뉴스 출처 수집 상태 조회")
  @GetMapping("/sources")
  public ResponseEntity<List<NewsSourceStatusResponse>> getSources() {
    return ResponseEntity.ok(sourceStatusService.getStatuses());
  }

  @Operation(
      summary = "공식 출처 갱신 요청",
      description = "외부 호출은 백그라운드 작업에서 실행하며, 활성 작업과 TTL 안의 성공 결과는 재사용한다.")
  @PostMapping("/refresh")
  public ResponseEntity<NewsRefreshJobResponse> refresh(
      @RequestParam(defaultValue = GithubReleaseNewsSource.SOURCE_KEY) String source) {
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(queueService.enqueue(source));
  }

  @Operation(summary = "뉴스 갱신 작업 상태 조회")
  @GetMapping("/refresh-jobs/{jobId}")
  public ResponseEntity<NewsRefreshJobResponse> getRefreshJob(@PathVariable String jobId) {
    return ResponseEntity.ok(lifecycleService.get(jobId));
  }
}
