package com.assetdashboard.domain.asset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assetdashboard.domain.asset.dto.AssetCreateRequest;
import com.assetdashboard.domain.asset.dto.AssetCreationResult;
import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.entity.AssetType;
import com.assetdashboard.domain.asset.repository.AssetRepository;
import com.assetdashboard.domain.transaction.repository.TransactionRepository;
import com.assetdashboard.global.exception.BusinessException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssetRegistrarTest {

  @Mock private AssetRepository assetRepository;
  @Mock private TransactionRepository transactionRepository;

  private AssetRegistrar assetRegistrar;

  @BeforeEach
  void setUp() {
    assetRegistrar = new AssetRegistrar(assetRepository, transactionRepository);
  }

  @Test
  void freshCryptoRegistrationKeepsOriginalAndKrwAveragePrices() {
    AssetCreateRequest request = bitcoinRequest("1", "60000", "1400");
    when(assetRepository.findByUserIdAndTypeAndSymbol(1L, AssetType.CRYPTO, "BTC"))
        .thenReturn(Optional.empty());

    AssetCreationResult result = assetRegistrar.persist(1L, request, "BTC", null, null);

    assertThat(result.restored()).isFalse();
    assertThat(result.asset().avgPriceOriginal()).isEqualByComparingTo("60000");
    assertThat(result.asset().avgPrice()).isEqualByComparingTo("84000000");
    assertThat(result.asset().costBasisMissing()).isFalse();
    verify(assetRepository).save(any(Asset.class));
  }

  @Test
  void restoringAssetWithoutTransactionsAppliesNewOpeningAveragePrice() {
    Asset deleted = Asset.create(1L, AssetType.CRYPTO, "BTC", "비트코인", "USDT");
    deleted.initializePosition(BigDecimal.ONE, null, null);
    deleted.softDelete();
    when(assetRepository.findByUserIdAndTypeAndSymbol(1L, AssetType.CRYPTO, "BTC"))
        .thenReturn(Optional.of(deleted));
    when(transactionRepository.existsByAssetId(deleted.getId())).thenReturn(false);

    AssetCreationResult result =
        assetRegistrar.persist(1L, bitcoinRequest("1", "60000", "1400"), "BTC", null, null);

    assertThat(result.restored()).isTrue();
    assertThat(result.asset().avgPriceOriginal()).isEqualByComparingTo("60000");
    assertThat(result.asset().avgPrice()).isEqualByComparingTo("84000000");
    assertThat(result.asset().costBasisMissing()).isFalse();
    verify(assetRepository, never()).save(any());
  }

  @Test
  void restoringAssetWithTransactionsRejectsSilentlyIgnoredOpeningValues() {
    Asset deleted = Asset.create(1L, AssetType.CRYPTO, "BTC", "비트코인", "USDT");
    deleted.initializePosition(BigDecimal.ONE, null, null);
    deleted.softDelete();
    when(assetRepository.findByUserIdAndTypeAndSymbol(1L, AssetType.CRYPTO, "BTC"))
        .thenReturn(Optional.of(deleted));
    when(transactionRepository.existsByAssetId(deleted.getId())).thenReturn(true);

    assertThatThrownBy(
            () ->
                assetRegistrar.persist(
                    1L, bitcoinRequest("1", "60000", "1400"), "BTC", null, null))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.getMessage()).contains("거래 내역", "수량·평단을 비우고"));

    assertThat(deleted.isDeleted()).isTrue();
  }

  private AssetCreateRequest bitcoinRequest(String quantity, String average, String rate) {
    return new AssetCreateRequest(
        AssetType.CRYPTO,
        "BTC",
        null,
        "비트코인",
        "USDT",
        new BigDecimal(quantity),
        new BigDecimal(average),
        new BigDecimal(rate));
  }
}
