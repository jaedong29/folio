package com.assetdashboard.evidence.news;

import static org.assertj.core.api.Assertions.assertThat;

import com.assetdashboard.evidence.calculation.EvidenceConclusion;
import com.assetdashboard.news.NewsCategory;
import com.assetdashboard.news.NewsSourceType;
import com.assetdashboard.news.NewsTrust;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NewsAnswerGuardrailTest {

  private final NewsAnswerGuardrail guardrail = new NewsAnswerGuardrail();

  @Test
  void blocksUnsupportedPriceCausalityAndKeepsSourceTitle() {
    NewsAnswerGuardrail.GuardrailDecision decision =
        guardrail.apply("이 뉴스 때문에 상승했습니다.", available());

    assertThat(decision.replaced()).isTrue();
    assertThat(decision.violationCode()).isEqualTo("UNSUPPORTED_NEWS_CAUSALITY");
    assertThat(decision.answer()).contains("Zebra 3.0.0", "인과관계는 확인할 수 없습니다");
  }

  @Test
  void unavailableEvidenceNeverCallsForAConfidentSummary() {
    NewsEvidenceResponse unavailable =
        new NewsEvidenceResponse(
            "trace", 42L, "ZEC", 0, EvidenceConclusion.UNAVAILABLE,
            Instant.parse("2026-09-03T00:00:00Z"), List.of(), List.of("자료 없음"));

    NewsAnswerGuardrail.GuardrailDecision decision = guardrail.apply(null, unavailable);

    assertThat(decision.replaced()).isTrue();
    assertThat(decision.answer()).contains("확인할 수 없습니다");
  }

  private NewsEvidenceResponse available() {
    NewsEvidenceItem item =
        new NewsEvidenceItem(
            1L,
            NewsCategory.CRYPTO,
            NewsSourceType.OFFICIAL_RELEASE,
            NewsTrust.VERIFIED_OFFICIAL,
            "Zebra 3.0.0",
            "Zcash Foundation",
            "https://example.com/release",
            Instant.parse("2026-09-02T00:00:00Z"),
            "Security release",
            Set.of("RELEASE"),
            true);
    return new NewsEvidenceResponse(
        "trace", 42L, "ZEC", 1, EvidenceConclusion.PARTIAL,
        Instant.parse("2026-09-03T00:00:00Z"), List.of(item), List.of());
  }
}
