package com.assetdashboard.evidence.calculation;

/** 가격·환율 한 값의 가용성과 신선도를 표현한다. */
public enum EvidenceValueStatus {
  FRESH,
  STALE,
  MISSING,
  FIXED
}
