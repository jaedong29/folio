package com.assetdashboard.domain.transaction.repository;

import com.assetdashboard.domain.transaction.entity.Transaction;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Transaction 영속성 접근.
 *
 * <p>Transaction 은 별도 Aggregate 가 아니지만 Repository 는 따로 둔다(PRD 1장의 "실용적 절충"). 거래는
 * append-only 로그라 건수가 무한히 증가하므로 Asset 의 {@code @OneToMany} 컬렉션으로 묶으면 Asset 을 로드할
 * 때마다 전체 내역이 따라올 위험이 있다. 대신 <b>조회·변경의 진입점</b>은 언제나 Asset 이라는 규칙을 지켜
 * Aggregate 의 본질적 이점은 유지한다.
 *
 * <p>따라서 이 Repository 의 조회 메서드는 {@code assetId} 를 반드시 받으며, 그 assetId 의 소유권은 호출 전에
 * {@code AssetRepository} 가 이미 검증한 상태여야 한다.
 */
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

  /**
   * 특정 자산의 거래 내역을 최신순으로 조회한다.
   *
   * @param assetId 자산 id (소유권이 이미 검증된 값)
   * @return 거래 목록 (tradedAt 내림차순)
   */
  List<Transaction> findAllByAssetIdOrderByTradedAtDescIdDesc(Long assetId);

  /**
   * 여러 자산의 거래 내역을 최신순으로 조회한다. Dashboard 의 "최근 거래 N건"에 사용한다.
   *
   * @param assetIds 조회 대상 자산 id 목록 (모두 소유권이 검증된 값)
   * @param pageable 조회 건수 제한
   * @return 거래 목록 (tradedAt 내림차순)
   */
  List<Transaction> findAllByAssetIdInOrderByTradedAtDescIdDesc(
      List<Long> assetIds, Pageable pageable);
}
