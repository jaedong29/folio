package com.assetdashboard.domain.transaction.repository;

import com.assetdashboard.domain.transaction.entity.Transaction;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

  /** 여러 자산에 속한 거래를 계정 탈퇴 전에 삭제한다.
   *
   * @param assetIds 삭제할 자산 id 목록
   */
  void deleteAllByAssetIdIn(List<Long> assetIds);

  /**
   * 특정 자산에 거래가 한 건이라도 있는지 확인한다.
   *
   * <p>삭제했던 자산을 재등록할 때 새 최초 보유상태를 적용해도 되는지 판정하는 데 사용한다. 거래가 이미 있으면
   * 최초 상태만 덮어쓸 경우 이력과 현재 Position이 어긋나므로 기존 상태를 그대로 복구해야 한다.
   *
   * @param assetId 자산 id
   * @return 거래가 하나 이상 있으면 true
   */
  boolean existsByAssetId(Long assetId);

  /**
   * 특정 자산의 거래 내역을 최신순으로 조회한다.
   *
   * @param assetId 자산 id (소유권이 이미 검증된 값)
   * @return 거래 목록 (tradedAt 내림차순)
   */
  List<Transaction> findAllByAssetIdOrderByTradedAtDescIdDesc(Long assetId);

  /**
   * Agent 근거 응답에 포함할 최근 거래를 제한된 크기로 조회한다.
   *
   * @param assetId 자산 id (소유권이 이미 검증된 값)
   * @param pageable 페이지와 최대 건수
   * @return 최근 거래 페이지와 전체 거래 건수
   */
  Page<Transaction> findAllByAssetIdOrderByTradedAtDescIdDesc(
      Long assetId, Pageable pageable);

  /**
   * 특정 자산의 거래 내역을 <b>거래 시점 오름차순</b>으로 조회한다.
   *
   * <p>거래 삭제 후 상태를 다시 계산할 때 쓴다. 같은 {@code tradedAt} 이면 입력 순서(id)로 안정 정렬해,
   * 재계산 결과가 실행할 때마다 달라지지 않게 한다.
   *
   * @param assetId 자산 id (소유권이 이미 검증된 값)
   * @return 거래 목록 (tradedAt 오름차순, 동률이면 id 오름차순)
   */
  List<Transaction> findAllByAssetIdOrderByTradedAtAscIdAsc(Long assetId);

  /**
   * 주어진 시점보다 나중에 일어난 거래가 이미 존재하는지 확인한다.
   *
   * <p>새 거래가 이력의 <b>중간에 끼워 넣어지는지</b>(과거 시점 거래를 뒤늦게 입력하는 경우)를 판별하는 데 쓴다.
   * 맨 뒤에 붙는 거래는 증분 계산으로 충분하지만, 중간에 끼면 그 이후 계산의 전제가 바뀌므로 전체를 다시 접어야
   * 한다.
   *
   * @param assetId 자산 id
   * @param tradedAt 기준 거래 시점
   * @return 이 시점보다 나중의 거래가 있으면 {@code true}
   */
  boolean existsByAssetIdAndTradedAtGreaterThan(Long assetId, LocalDateTime tradedAt);

  /**
   * 특정 자산에 속한 거래 한 건을 조회한다.
   *
   * <p>{@code assetId} 를 조건에 포함하는 이유는 자산 소유권 검사를 우회할 수 없게 하기 위해서다. 다른 사람의
   * 자산에 속한 거래 id 를 내 자산 경로로 넘겨도 조회되지 않는다.
   *
   * @param id 거래 id
   * @param assetId 자산 id (소유권이 이미 검증된 값)
   * @return 해당 자산의 거래이면 Transaction, 아니면 빈 Optional
   */
  Optional<Transaction> findByIdAndAssetId(Long id, Long assetId);

  /**
   * 여러 자산의 거래 내역을 최신순으로 조회한다. Dashboard 의 "최근 거래 N건"에 사용한다.
   *
   * @param assetIds 조회 대상 자산 id 목록 (모두 소유권이 검증된 값)
   * @param pageable 조회 건수 제한
   * @return 거래 목록 (tradedAt 내림차순)
   */
  List<Transaction> findAllByAssetIdInOrderByTradedAtDescIdDesc(
      List<Long> assetIds, Pageable pageable);

  /**
   * 지정 구간의 외부 입출금만 조회한다. BUY/SELL은 Portfolio 내부 이동이므로 제외한다.
   *
   * @param assetIds 사용자의 자산 id 목록
   * @param from 기준 Snapshot 시각
   * @param to 현재 계산 시각
   * @return DEPOSIT/WITHDRAW 이벤트
   */
  @Query(
      "select t from Transaction t "
          + "where t.assetId in :assetIds "
          + "and t.type in (com.assetdashboard.domain.transaction.entity.TransactionType.DEPOSIT, "
          + "com.assetdashboard.domain.transaction.entity.TransactionType.WITHDRAW) "
          + "and t.tradedAt > :from and t.tradedAt <= :to")
  List<Transaction> findExternalFlows(
      @Param("assetIds") List<Long> assetIds,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);
}
