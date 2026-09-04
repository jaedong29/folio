package com.assetdashboard.news;

public record NewsIngestionResult(
    int discoveredCount, int insertedCount, int updatedCount, int unchangedCount) {}
