package com.assetdashboard.infra.price.crypto;

import com.assetdashboard.domain.asset.entity.AssetType;
import com.assetdashboard.infra.price.PriceProvider;
import com.assetdashboard.infra.price.PriceProviderException;
import com.assetdashboard.infra.price.PriceQuote;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 암호화폐 시세를 {@code Binance USDT 가격 × Upbit KRW-USDT 환율} 로 계산한다.
 *
 * <p>거래소별 개별 시세(예: Upbit 의 KRW-BTC)를 그대로 쓰지 않는 이유는, 코인마다 입출금 제한 등으로 거래소 간
 * 가격이 비정상적으로 벌어지는 구간이 생기기 때문이다. 특정 거래소의 가격을 그대로 신뢰하는 대신 모든 코인에 동일한
 * 계산 방식을 적용해, 개별 코인의 가격 왜곡에 흔들리지 않게 한다(PRD 1장).
 *
 * <p>두 클라이언트 중 하나라도 실패하면 {@link PriceProviderException} 을 던진다. 절반만 성공한 값(가격은
 * 새것, 환율은 옛것)으로 평가금액을 만들면 조용히 틀린 숫자가 나오기 때문이다.
 */
@Component
@RequiredArgsConstructor
public class CryptoPriceProvider implements PriceProvider {

  private final BinanceClient binanceClient;
  private final UpbitExchangeRateClient upbitExchangeRateClient;

  @Override
  public boolean supports(AssetType type) {
    return type == AssetType.CRYPTO;
  }

  @Override
  public PriceQuote fetchPrice(String symbol) {
    BigDecimal usdtPrice = binanceClient.fetchUsdtPrice(symbol);
    BigDecimal krwPerUsdt = upbitExchangeRateClient.fetchKrwPerUsdt();
    // 가격은 원래 통화(USDT) 기준으로 두고 환율을 함께 돌려준다.
    // 평가금액 = quantity × price × exchangeRate 라는 공통 공식이 그대로 성립한다.
    return PriceQuote.of(usdtPrice, krwPerUsdt);
  }
}
