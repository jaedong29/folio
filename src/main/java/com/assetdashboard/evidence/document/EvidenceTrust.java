package com.assetdashboard.evidence.document;

/**
 * URL 도메인 검증 결과와 사용자 주장을 구분하는 자료 신뢰 표기.
 *
 * <p>사용자가 OFFICIAL을 선택했다고 시스템이 공식 출처로 확정하지 않는다.
 */
public enum EvidenceTrust {
  VERIFIED_OFFICIAL,
  USER_ASSERTED_OFFICIAL,
  USER_ASSERTED_NEWS,
  USER_PROVIDED
}
