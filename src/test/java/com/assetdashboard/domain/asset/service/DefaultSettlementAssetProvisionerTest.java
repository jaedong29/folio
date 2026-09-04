package com.assetdashboard.domain.asset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.entity.AssetType;
import com.assetdashboard.domain.asset.repository.AssetRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DefaultSettlementAssetProvisionerTest {

  @Test
  void createsMissingKrwUsdAndUsdtCashWithZeroBalance() {
    AssetRepository repository = mock(AssetRepository.class);
    when(repository.findByUserIdAndTypeAndSymbol(eq(7L), eq(AssetType.CASH), any()))
        .thenReturn(Optional.empty());
    DefaultSettlementAssetProvisioner provisioner =
        new DefaultSettlementAssetProvisioner(repository);

    provisioner.ensureDefaults(7L);

    ArgumentCaptor<Asset> captor = ArgumentCaptor.forClass(Asset.class);
    verify(repository, times(3)).save(captor.capture());
    List<Asset> created = captor.getAllValues();
    assertThat(created).extracting(Asset::getSymbol).containsExactly("KRW", "USD", "USDT");
    assertThat(created).allMatch(Asset::isDefaultSettlementAsset);
    assertThat(created).allMatch(asset -> asset.getQuantity().compareTo(BigDecimal.ZERO) == 0);
  }
}
