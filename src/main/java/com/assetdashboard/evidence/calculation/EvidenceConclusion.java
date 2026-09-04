package com.assetdashboard.evidence.calculation;

/** 계산 근거를 바탕으로 현재 답변을 어느 수준까지 확정할 수 있는지 나타낸다. */
public enum EvidenceConclusion {
  /** 필요한 가격·환율·원가를 모두 확보했다. */
  CONFIRMED,

  /** 계산은 가능하지만 오래된 값이 있거나 일부 손익 근거가 빠져 있다. */
  PARTIAL,

  /** 가격 또는 환율이 없어 현재 평가금액을 계산할 수 없다. */
  UNAVAILABLE
}
