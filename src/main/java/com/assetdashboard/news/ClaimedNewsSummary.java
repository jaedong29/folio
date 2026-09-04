package com.assetdashboard.news;

/** 트랜잭션 밖의 LLM 호출에 필요한 공개 문서 스냅샷. */
public record ClaimedNewsSummary(
    Long newsItemId,
    String contentHash,
    String title,
    String publisher,
    String content) {}
