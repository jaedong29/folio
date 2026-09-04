package com.assetdashboard.evidence.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.assetdashboard.domain.user.entity.User;
import com.assetdashboard.domain.user.repository.UserRepository;
import com.assetdashboard.evidence.calculation.EvidenceConclusion;
import com.assetdashboard.evidence.tool.AssetEvidenceToolAdapter;
import com.assetdashboard.evidence.tool.AssetEvidenceToolResult;
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
            Set.of("COST_BASIS_MISSING", "unrealizedPnlKrw=null")));
  }
}
