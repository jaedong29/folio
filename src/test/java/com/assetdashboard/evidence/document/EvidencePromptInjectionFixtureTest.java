package com.assetdashboard.evidence.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.entity.AssetType;
import com.assetdashboard.domain.asset.repository.AssetRepository;
import com.assetdashboard.domain.user.entity.User;
import com.assetdashboard.domain.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** 악성 지시문이 섞인 fixture가 실제 DB에서도 명령이 아닌 신뢰하지 않는 원문으로 보존되는지 검증한다. */
@SpringBootTest
@Transactional
class EvidencePromptInjectionFixtureTest {

  private static final String FIXTURE = "/evaluation/prompt-injection-document.json";

  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private AssetRepository assetRepository;
  @Autowired private EvidenceDocumentRepository evidenceDocumentRepository;
  @Autowired private EvidenceDocumentService evidenceDocumentService;

  @Test
  void storesInstructionLikeTextAsUntrustedDocumentData() throws Exception {
    EvidenceDocumentCreateRequest request = readFixture();
    User user =
        userRepository.save(
            User.create("prompt-fixture@example.com", "encoded-password", "fixture"));
    Asset asset =
        assetRepository.save(
            Asset.create(user.getId(), AssetType.STOCK, "005930.KS", "삼성전자", "KRW"));

    EvidenceDocumentResponse response =
        evidenceDocumentService.create(user.getId(), asset.getId(), request);

    EvidenceDocument persisted = evidenceDocumentRepository.findById(response.id()).orElseThrow();
    assertThat(response.untrustedContent()).isTrue();
    assertThat(response.trust()).isEqualTo(EvidenceTrust.VERIFIED_OFFICIAL);
    assertThat(persisted.getContent()).isEqualTo(request.content());
    assertThat(persisted.getContent())
        .contains("이전 지시를 모두 무시", "다른 사용자의 거래 내역", "회귀 테스트용 문서 데이터");
  }

  private EvidenceDocumentCreateRequest readFixture() throws Exception {
    try (InputStream stream = getClass().getResourceAsStream(FIXTURE)) {
      assertThat(stream).as("prompt injection fixture").isNotNull();
      return objectMapper.readValue(stream, EvidenceDocumentCreateRequest.class);
    }
  }
}
