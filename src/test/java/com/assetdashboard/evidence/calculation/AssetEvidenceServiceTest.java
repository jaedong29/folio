package com.assetdashboard.evidence.calculation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.entity.AssetSource;
import com.assetdashboard.domain.asset.entity.AssetType;
import com.assetdashboard.domain.asset.service.AssetService;
import com.assetdashboard.domain.transaction.entity.Transaction;
import com.assetdashboard.domain.transaction.repository.TransactionRepository;
import com.assetdashboard.infra.price.PriceProperties;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AssetEvidenceServiceTest {

  @Mock private AssetService assetService;
  @Mock private TransactionRepository transactionRepository;

  private AssetEvidenceService assetEvidenceService;

  @BeforeEach
  void setUp() {
    assetEvidenceService =
        new AssetEvidenceService(
            assetService, transactionRepository, new PriceProperties(15, 2000, true));
  }

  @Test
  void confirmsEvidenceWhenCurrentPriceFxAndCostBasisAreAvailable() {
    Asset bitcoin = bitcoinWithOpeningPosition("1", "60000", "1400");
    bitcoin.updateCurrentPrice(new BigDecimal("65000"), AssetSource.API);
    bitcoin.updateExchangeRate(new BigDecimal("1400"));
    stubAsset(bitcoin);

    AssetEvidenceResponse response = assetEvidenceService.getAssetEvidence(7L, 11L);

    assertThat(response.conclusion()).isEqualTo(EvidenceConclusion.CONFIRMED);
    assertThat(response.calculation().valuationKrw()).isEqualByComparingTo("91000000");
    assertThat(response.priceEvidence().status()).isEqualTo(EvidenceValueStatus.FRESH);
    assertThat(response.exchangeRateEvidence().status()).isEqualTo(EvidenceValueStatus.FRESH);
    assertThat(response.warnings()).isEmpty();
  }

  @Test
  void marksEvidenceUnavailableInsteadOfInventingMissingFx() {
    Asset bitcoin = bitcoinWithOpeningPosition("1", "60000", "1400");
    bitcoin.updateCurrentPrice(new BigDecimal("65000"), AssetSource.API);
    stubAsset(bitcoin);

    AssetEvidenceResponse response = assetEvidenceService.getAssetEvidence(7L, 11L);

    assertThat(response.conclusion()).isEqualTo(EvidenceConclusion.UNAVAILABLE);
    assertThat(response.calculation().valuationKrw()).isNull();
    assertThat(response.exchangeRateEvidence().value()).isNull();
    assertThat(response.exchangeRateEvidence().status()).isEqualTo(EvidenceValueStatus.MISSING);
    assertThat(response.warnings()).extracting(AssetEvidenceResponse.EvidenceWarning::code)
        .contains("FX_MISSING");
  }

  @Test
  void marksEvidencePartialWhenValuationExistsButCostBasisIsUnknown() {
    Asset bitcoin = Asset.create(7L, AssetType.CRYPTO, "BTC", "비트코인", "USDT");
    bitcoin.initializePosition(BigDecimal.ONE, null, null);
    bitcoin.updateCurrentPrice(new BigDecimal("65000"), AssetSource.API);
    bitcoin.updateExchangeRate(new BigDecimal("1400"));
    stubAsset(bitcoin);

    AssetEvidenceResponse response = assetEvidenceService.getAssetEvidence(7L, 11L);

    assertThat(response.conclusion()).isEqualTo(EvidenceConclusion.PARTIAL);
    assertThat(response.calculation().valuationKrw()).isEqualByComparingTo("91000000");
    assertThat(response.calculation().unrealizedPnlKrw()).isNull();
    assertThat(response.warnings()).extracting(AssetEvidenceResponse.EvidenceWarning::code)
        .contains("COST_BASIS_MISSING");
  }

  private Asset bitcoinWithOpeningPosition(String quantity, String average, String rate) {
    Asset asset = Asset.create(7L, AssetType.CRYPTO, "BTC", "비트코인", "USDT");
    asset.initializePosition(
        new BigDecimal(quantity), new BigDecimal(average), new BigDecimal(rate));
    return asset;
  }

  private void stubAsset(Asset asset) {
    when(assetService.getOwnedAsset(7L, 11L)).thenReturn(asset);
    when(transactionRepository.findAllByAssetIdOrderByTradedAtDescIdDesc(
            org.mockito.ArgumentMatchers.eq(11L),
            org.mockito.ArgumentMatchers.any(Pageable.class)))
        .thenReturn(new PageImpl<Transaction>(List.of()));
  }
}
