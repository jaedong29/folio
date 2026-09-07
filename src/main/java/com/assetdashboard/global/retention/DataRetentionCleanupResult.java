package com.assetdashboard.global.retention;

/** 한 번의 보존 기간 정리 작업에서 제거한 루트 기록 수. */
public record DataRetentionCleanupResult(
    long auditLogCount, int agentTraceCount, int evaluationBatchCount) {}
