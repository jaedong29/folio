package com.assetdashboard.news;

import java.util.List;
import java.util.Set;

/** 약관과 신뢰 수준을 검토한 공용 자료 수집 Adapter. */
public interface OfficialNewsSource {

  String key();

  String displayName();

  NewsCategory category();

  Set<String> symbols();

  List<CollectedNewsItem> fetch();
}
