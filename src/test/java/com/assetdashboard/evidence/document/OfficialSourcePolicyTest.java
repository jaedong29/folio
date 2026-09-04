package com.assetdashboard.evidence.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OfficialSourcePolicyTest {

  private OfficialSourcePolicy policy;

  @BeforeEach
  void setUp() {
    policy =
        new OfficialSourcePolicy(
            new EvidenceProperties(
                List.of("dart.fss.or.kr", "kind.krx.co.kr", "sec.gov")));
  }

  @Test
  void verifiesExactOfficialHostAndItsSubdomain() {
    assertThat(
            policy.resolveTrust(
                EvidenceSourceType.OFFICIAL,
                "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=1"))
        .isEqualTo(EvidenceTrust.VERIFIED_OFFICIAL);
    assertThat(
            policy.resolveTrust(
                EvidenceSourceType.OFFICIAL,
                "https://www.sec.gov/Archives/edgar/data/example"))
        .isEqualTo(EvidenceTrust.VERIFIED_OFFICIAL);
  }

  @Test
  void doesNotTrustSuffixSpoofingDomain() {
    assertThat(
            policy.resolveTrust(
                EvidenceSourceType.OFFICIAL,
                "https://www.sec.gov.attacker.example/filing"))
        .isEqualTo(EvidenceTrust.USER_ASSERTED_OFFICIAL);
    assertThat(
            policy.resolveTrust(
                EvidenceSourceType.OFFICIAL,
                "https://kind.krx.co.kr.attacker.example/disclosure"))
        .isEqualTo(EvidenceTrust.USER_ASSERTED_OFFICIAL);
  }

  @Test
  void newsIsNotPromotedEvenWhenHostedOnOfficialDomain() {
    assertThat(
            policy.resolveTrust(
                EvidenceSourceType.NEWS,
                "https://www.sec.gov/news/press-release"))
        .isEqualTo(EvidenceTrust.USER_ASSERTED_NEWS);
  }
}
