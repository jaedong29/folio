package com.assetdashboard.global.resilience;

/** Circuit Breaker와 재시도 상태를 서로 섞지 않는 외부 호출 출처 식별자. */
public enum ExternalSource {
  YAHOO_FINANCE,
  BINANCE,
  UPBIT,
  GITHUB_RELEASES,
  NVIDIA_NIM
}
