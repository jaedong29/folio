package com.assetdashboard.evidence.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.assetdashboard.domain.user.entity.User;
import com.assetdashboard.domain.user.repository.UserRepository;
import com.assetdashboard.evidence.calculation.EvidenceConclusion;
import com.assetdashboard.evidence.document.EvidenceTrust;
import com.assetdashboard.evidence.document.SymbolEvidenceToolAdapter;
import com.assetdashboard.evidence.document.SymbolEvidenceToolResult;
import com.assetdashboard.evidence.tool.AssetEvidenceToolAdapter;
import com.assetdashboard.evidence.tool.AssetEvidenceToolResult;
import com.assetdashboard.evidence.news.NewsEvidenceToolAdapter;
import com.assetdashboard.evidence.news.NewsEvidenceToolResult;
import com.assetdashboard.evidence.trend.PriceTrendDirection;
import com.assetdashboard.evidence.trend.PriceTrendEvidenceToolAdapter;
import com.assetdashboard.evidence.trend.PriceTrendEvidenceToolResult;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class FinancialAgentEvaluationFixtureServiceTest {

  @Autowired private UserRepository userRepository;
  @Autowired private FinancialAgentEvaluationFixtureService fixtureService;
  @Autowired private AssetEvidenceToolAdapter toolAdapter;
  @Autowired private NewsEvidenceToolAdapter newsToolAdapter;
  @Autowired private PriceTrendEvidenceToolAdapter trendToolAdapter;
  @Autowired private SymbolEvidenceToolAdapter symbolEvidenceToolAdapter;

  @ParameterizedTest(name = "{0} fixture는 {1} 결론과 결정적 fact를 만든다")
  @MethodSource("fixtureCases")
  void createsRealEvidenceWithoutExternalPriceCalls(
      String caseId, EvidenceConclusion conclusion, Set<String> requiredFacts) {
    User user =
        userRepository.save(
            User.create("nim-eval-" + caseId + "@example.com", "encoded", "eval"));

    EvaluationFixtureResponse fixture = fixtureService.create(user.getId(), caseId);
    AssetEvidenceToolResult result = toolAdapter.execute(user.getId(), fixture.assetId());

    assertThat(fixture.caseId()).isEqualTo(caseId);
    assertThat(result.grounding().conclusion()).isEqualTo(conclusion);
    assertThat(result.grounding().evidenceFacts()).containsAll(requiredFacts);
  }

  @org.junit.jupiter.api.Test
  void createsOfficialNewsFixtureWithoutAnotherModelCall() {
    User user =
        userRepository.save(User.create("nim-eval-news@example.com", "encoded", "eval"));

    EvaluationFixtureResponse fixture = fixtureService.create(user.getId(), "symbol-official-news");
    NewsEvidenceToolResult result = newsToolAdapter.execute(user.getId(), fixture.assetId());

    assertThat(result.grounding().conclusion()).isEqualTo(EvidenceConclusion.PARTIAL);
    assertThat(result.grounding().evidenceFacts())
        .contains("NEWS_AVAILABLE", "VERIFIED_OFFICIAL", "untrustedContent=true");
    assertThat(result.payload().items()).hasSize(1);
  }

  @org.junit.jupiter.api.Test
  void createsPriceDirectionFixtureWithoutCallingYahooOrBinance() {
    User user = userRepository.save(User.create("nim-eval-trend@example.com", "encoded", "eval"));

    EvaluationFixtureResponse fixture = fixtureService.create(user.getId(), "price-direction");
    PriceTrendEvidenceToolResult result = trendToolAdapter.execute(user.getId(), fixture.assetId());

    assertThat(result.grounding().conclusion()).isEqualTo(EvidenceConclusion.CONFIRMED);
    assertThat(result.payload().direction()).isEqualTo(PriceTrendDirection.UP);
    assertThat(result.grounding().evidenceFacts())
        .contains(
            "PRICE_TREND_AVAILABLE",
            "direction",
            "returnRatePercent",
            "startPrice",
            "endPrice",
            "pointCount",
            "directionRule");
  }

  @ParameterizedTest(name = "{0} fixture는 {1} 결론과 결정적 fact를 만든다 (symbol evidence)")
  @MethodSource("symbolEvidenceFixtureCases")
  void createsSymbolEvidenceWithoutLeakingOtherUsersDocuments(
      String caseId, EvidenceConclusion conclusion, Set<String> requiredFacts) {
    User user =
        userRepository.save(
            User.create("nim-eval-" + caseId + "@example.com", "encoded", "eval"));

    EvaluationFixtureResponse fixture = fixtureService.create(user.getId(), caseId);
    SymbolEvidenceToolResult result =
        symbolEvidenceToolAdapter.execute(user.getId(), fixture.assetId());

    assertThat(fixture.caseId()).isEqualTo(caseId);
    assertThat(result.grounding().conclusion()).isEqualTo(conclusion);
    assertThat(result.grounding().evidenceFacts()).containsAll(requiredFacts);
  }

  @org.junit.jupiter.api.Test
  void crossUserDocumentFixtureNeverLeaksTheOtherUsersTitleOrContent() {
    User user =
        userRepository.save(User.create("nim-eval-cross-user@example.com", "encoded", "eval"));

    EvaluationFixtureResponse fixture = fixtureService.create(user.getId(), "cross-user-document");
    SymbolEvidenceToolResult result =
        symbolEvidenceToolAdapter.execute(user.getId(), fixture.assetId());

    assertThat(result.grounding().conclusion()).isEqualTo(EvidenceConclusion.UNAVAILABLE);
    assertThat(result.payload().items()).isEmpty();
  }

  private static Stream<Arguments> symbolEvidenceFixtureCases() {
    return Stream.of(
        Arguments.of(
            "no-symbol-evidence",
            EvidenceConclusion.UNAVAILABLE,
            Set.of("EVIDENCE_UNAVAILABLE", "documentCount=0")),
        Arguments.of(
            "user-asserted-official",
            EvidenceConclusion.PARTIAL,
            Set.of(EvidenceTrust.USER_ASSERTED_OFFICIAL.name(), "documentId")),
        Arguments.of(
            "verified-dart",
            EvidenceConclusion.CONFIRMED,
            Set.of(EvidenceTrust.VERIFIED_OFFICIAL.name(), "documentId", "sourceUrl")),
        Arguments.of(
            "verified-kind",
            EvidenceConclusion.CONFIRMED,
            Set.of(EvidenceTrust.VERIFIED_OFFICIAL.name(), "documentId", "publishedAt")),
        Arguments.of(
            "verified-sec",
            EvidenceConclusion.CONFIRMED,
            Set.of(EvidenceTrust.VERIFIED_OFFICIAL.name(), "documentId", "sourceUrl")),
        Arguments.of(
            "prompt-injection",
            EvidenceConclusion.PARTIAL,
            Set.of("untrustedContent=true", "documentId")),
        Arguments.of(
            "cross-user-document",
            EvidenceConclusion.UNAVAILABLE,
            Set.of("EVIDENCE_UNAVAILABLE", "documentCount=0")));
  }

  private static Stream<Arguments> fixtureCases() {
    return Stream.of(
        Arguments.of(
            "fresh-valuation",
            EvidenceConclusion.CONFIRMED,
            Set.of("quantity", "currentPrice", "exchangeRate", "valuationRule", "traceId")),
        Arguments.of(
            "missing-price",
            EvidenceConclusion.UNAVAILABLE,
            Set.of("PRICE_MISSING", "currentPrice=null", "valuationKrw=null")),
        Arguments.of(
            "missing-fx",
            EvidenceConclusion.UNAVAILABLE,
            Set.of("FX_MISSING", "exchangeRate=null", "valuationKrw=null")),
        Arguments.of(
            "missing-cost-basis",
            EvidenceConclusion.PARTIAL,
            Set.of("COST_BASIS_MISSING", "unrealizedPnlKrw=null")),
        Arguments.of(
            "stale-price", EvidenceConclusion.PARTIAL, Set.of("PRICE_STALE", "priceUpdatedAt")),
        Arguments.of(
            "stale-fx", EvidenceConclusion.PARTIAL, Set.of("FX_STALE", "exchangeRateUpdatedAt")),
        Arguments.of(
            "transaction-evidence",
            EvidenceConclusion.CONFIRMED,
            Set.of("transactionId", "tradedAt", "totalCount", "truncated")));
  }
}
