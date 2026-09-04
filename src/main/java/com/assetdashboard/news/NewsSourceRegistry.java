package com.assetdashboard.news;

import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 검토를 마친 공식 출처만 수집 작업에서 선택할 수 있게 하는 화이트리스트. */
@Component
public class NewsSourceRegistry {

  private final Map<String, OfficialNewsSource> sources;

  public NewsSourceRegistry(List<OfficialNewsSource> sourceList) {
    Map<String, OfficialNewsSource> indexed = new LinkedHashMap<>();
    for (OfficialNewsSource source : sourceList) {
      if (indexed.put(source.key(), source) != null) {
        throw new IllegalStateException("중복된 뉴스 sourceKey입니다: " + source.key());
      }
    }
    this.sources = Map.copyOf(indexed);
  }

  public OfficialNewsSource require(String sourceKey) {
    OfficialNewsSource source = sources.get(sourceKey);
    if (source == null) {
      throw new BusinessException(ErrorCode.NEWS_SOURCE_NOT_FOUND);
    }
    return source;
  }

  public List<OfficialNewsSource> all() {
    return List.copyOf(sources.values());
  }
}
