package com.assetdashboard.domain.asset.repository;

import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.entity.AssetType;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Asset 영속성 접근.
 *
 * <p><b>단건 조회 메서드는 예외 없이 {@code userId} 를 파라미터로 받는다.</b> 소유권 검증을 "나중에 한 줄 더 쓰는
 * 일"로 남겨두면 새 API 를 만들 때마다 빠뜨릴 수 있으므로, 조회 조건 자체에 넣어 <b>검증을 빠뜨리는 것이 구조적으로
 * 불가능</b>하게 만든다(PRD 4-0 규칙 2). {@code findById} 는 사용하지 않는다.
 */
public interface AssetRepository extends JpaRepository<Asset, Long> {

  /**
   * 시세 반영 대상 자산을 행 잠금과 함께 조회한다.
   *
   * <p>Dashboard와 Portfolio가 동시에 같은 시세를 반영하더라도 한 요청씩 짧게 적용하게 해 {@code @Version}
   * 충돌을 피한다. 외부 HTTP 호출은 이 메서드 호출 전에 끝나므로 잠금이 네트워크 대기를 포함하지 않는다.
   *
   * @param ids 시세 반영 대상 자산 id
   * @return 행 잠금이 걸린 자산 목록
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Asset a where a.id in :ids")
  List<Asset> findAllByIdForUpdate(@Param("ids") List<Long> ids);

  /**
   * 내 자산 한 건을 조회한다. 삭제된 자산은 없는 것으로 취급한다.
   *
   * @param id 자산 id
   * @param userId 인증된 사용자 id
   * @return 내 소유의 활성 자산이면 해당 Asset, 아니면 빈 Optional
   */
  Optional<Asset> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

  /**
   * 내 활성 자산 전체를 조회한다.
   *
   * @param userId 인증된 사용자 id
   * @return 활성 자산 목록
   */
  List<Asset> findAllByUserIdAndDeletedAtIsNull(Long userId);

  /**
   * 내 활성 자산을 종류로 필터링해 조회한다.
   *
   * @param userId 인증된 사용자 id
   * @param types 조회할 자산 종류
   * @return 활성 자산 목록
   */
  List<Asset> findAllByUserIdAndTypeInAndDeletedAtIsNull(Long userId, List<AssetType> types);

  /**
   * 삭제 여부와 무관하게 내 자산 전체를 조회한다.
   *
   * <p>Dashboard 의 누적 실현손익 집계에만 사용한다. 삭제된 자산의 실현손익까지 합산해야 "지금까지 얼마 벌었나"에
   * 답할 수 있기 때문이다.
   *
   * @param userId 인증된 사용자 id
   * @return 삭제된 자산을 포함한 전체 자산 목록
   */
  List<Asset> findAllByUserId(Long userId);

  /**
   * 중복 등록 여부를 확인하기 위해 삭제된 자산까지 포함해 조회한다.
   *
   * <p>{@code (user_id, type, symbol)} 유니크 제약이 {@code deleted_at} 을 포함하지 않으므로, 삭제된 자산도
   * 찾아내야 재등록을 부활 처리할 수 있다.
   *
   * @param userId 인증된 사용자 id
   * @param type 자산 종류
   * @param symbol 정규화된 심볼
   * @return 존재하면 해당 Asset (삭제된 것 포함)
   */
  Optional<Asset> findByUserIdAndTypeAndSymbol(Long userId, AssetType type, String symbol);

  /**
   * 특정 자산이 존재하고 내 소유이며 삭제되지 않았는지 확인한다.
   *
   * @param id 자산 id
   * @param userId 인증된 사용자 id
   * @return 조건을 모두 만족하면 {@code true}
   */
  boolean existsByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);
}
