package com.assetdashboard.news;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NewsItemRepository extends JpaRepository<NewsItem, Long> {

  Optional<NewsItem> findBySourceKeyAndExternalId(String sourceKey, String externalId);

  @Query(
      """
      select distinct n from NewsItem n
      where (:category is null or n.category = :category)
        and (:queryText is null
          or lower(n.title) like lower(concat('%', :queryText, '%'))
          or lower(n.publisher) like lower(concat('%', :queryText, '%'))
          or lower(n.excerpt) like lower(concat('%', :queryText, '%')))
      order by n.publishedAt desc, n.id desc
      """)
  List<NewsItem> searchAll(
      @Param("category") NewsCategory category,
      @Param("queryText") String queryText,
      Pageable pageable);

  @Query(
      """
      select distinct n from NewsItem n join n.symbols symbol
      where symbol in :symbols
        and (:category is null or n.category = :category)
        and (:queryText is null
          or lower(n.title) like lower(concat('%', :queryText, '%'))
          or lower(n.publisher) like lower(concat('%', :queryText, '%'))
          or lower(n.excerpt) like lower(concat('%', :queryText, '%')))
      order by n.publishedAt desc, n.id desc
      """)
  List<NewsItem> searchPortfolio(
      @Param("symbols") Collection<String> symbols,
      @Param("category") NewsCategory category,
      @Param("queryText") String queryText,
      Pageable pageable);

  @Query(
      """
      select distinct n from NewsItem n join n.symbols symbol
      where symbol = :symbol
      order by n.publishedAt desc, n.id desc
      """)
  List<NewsItem> findLatestBySymbol(
      @Param("symbol") String symbol, Pageable pageable);
}
