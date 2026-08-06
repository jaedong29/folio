package com.assetdashboard.domain.asset.entity;

/**
 * 자산 정보(주로 currentPrice)의 출처.
 *
 * <p>{@code MYDATA}, {@code WEB3} 는 아직 수집 로직이 없지만 Roadmap 7-3 확장 시 Asset 구조를 그대로 재사용하기
 * 위해 미리 정의해 둔다.
 */
public enum AssetSource {

  /** 사용자가 직접 입력. */
  MANUAL,

  /** CSV 업로드로 반입. */
  CSV,

  /** 외부 시세 API 자동 조회 성공. */
  API,

  /** MyData 연동 (Roadmap). */
  MYDATA,

  /** Web3 지갑 연동 (Roadmap). */
  WEB3
}
