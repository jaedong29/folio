package com.assetdashboard.domain.asset.controller;

import com.assetdashboard.domain.asset.dto.AssetCreateRequest;
import com.assetdashboard.domain.asset.dto.AssetCreationResult;
import com.assetdashboard.domain.asset.dto.AssetExchangeRateUpdateRequest;
import com.assetdashboard.domain.asset.dto.AssetPriceUpdateRequest;
import com.assetdashboard.domain.asset.dto.AssetResponse;
import com.assetdashboard.domain.asset.dto.AssetUpdateRequest;
import com.assetdashboard.domain.asset.service.AssetService;
import com.assetdashboard.global.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 자산 API (PRD 4-2).
 *
 * <p>모든 엔드포인트는 인증이 필요하며, {@code userId} 는 요청 본문이 아니라 {@link CurrentUserId} 를 통해
 * 검증된 JWT 에서만 얻는다(PRD 4-0 규칙 4).
 */
@Tag(name = "Asset", description = "자산 등록 / 조회 / 수정 / 삭제")
@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetController {

  private final AssetService assetService;

  /**
   * 자산을 등록한다.
   *
   * @param userId 인증된 사용자 id
   * @param request 등록 요청
   * @return 신규 등록이면 201, 삭제되었던 자산을 복구했으면 200
   */
  @Operation(
      summary = "자산 등록",
      description =
          "STOCK/CRYPTO 는 저장 전에 외부 시세 API 로 심볼을 1회 검증한다. "
              + "삭제했던 자산과 같은 심볼을 다시 등록하면 기존 자산을 복구하고 200 을 반환한다.")
  @PostMapping
  public ResponseEntity<AssetResponse> create(
      @CurrentUserId Long userId, @Valid @RequestBody AssetCreateRequest request) {
    AssetCreationResult result = assetService.create(userId, request);
    HttpStatus status = result.restored() ? HttpStatus.OK : HttpStatus.CREATED;
    return ResponseEntity.status(status).body(result.asset());
  }

  /**
   * 내 자산 목록을 조회한다.
   *
   * @param userId 인증된 사용자 id
   * @return 활성 자산 목록
   */
  @Operation(summary = "자산 목록 조회", description = "삭제된 자산은 제외된다.")
  @GetMapping
  public ResponseEntity<List<AssetResponse>> getAssets(@CurrentUserId Long userId) {
    return ResponseEntity.ok(assetService.getAssets(userId));
  }

  /**
   * 자산 한 건을 조회한다.
   *
   * @param userId 인증된 사용자 id
   * @param id 자산 id
   * @return 자산 정보
   */
  @Operation(summary = "자산 단건 조회", description = "타인 소유 자산은 존재하지 않는 것과 동일하게 404 로 응답한다.")
  @GetMapping("/{id}")
  public ResponseEntity<AssetResponse> getAsset(
      @CurrentUserId Long userId, @PathVariable Long id) {
    return ResponseEntity.ok(assetService.getAsset(userId, id));
  }

  /**
   * 표시 이름과 통화를 수정한다.
   *
   * @param userId 인증된 사용자 id
   * @param id 자산 id
   * @param request 수정 요청
   * @return 수정된 자산 정보
   */
  @Operation(summary = "자산 수정", description = "name, currency 만 수정 가능. symbol/type 은 불변이다.")
  @PatchMapping("/{id}")
  public ResponseEntity<AssetResponse> update(
      @CurrentUserId Long userId,
      @PathVariable Long id,
      @Valid @RequestBody AssetUpdateRequest request) {
    return ResponseEntity.ok(assetService.update(userId, id, request));
  }

  /**
   * 자산을 삭제한다 (Soft Delete).
   *
   * @param userId 인증된 사용자 id
   * @param id 자산 id
   * @return 본문 없는 204
   */
  @Operation(summary = "자산 삭제", description = "Soft Delete. 거래 내역과 실현손익은 보존된다.")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@CurrentUserId Long userId, @PathVariable Long id) {
    assetService.delete(userId, id);
    return ResponseEntity.noContent().build();
  }

  /**
   * 현재가를 수동으로 갱신한다.
   *
   * @param userId 인증된 사용자 id
   * @param id 자산 id
   * @param request 새 현재가
   * @return 갱신된 자산 정보
   */
  @Operation(summary = "현재가 수동 갱신", description = "자동 조회 실패 시의 최후 폴백 경로. Transaction 을 만들지 않는다.")
  @PatchMapping("/{id}/price")
  public ResponseEntity<AssetResponse> updatePrice(
      @CurrentUserId Long userId,
      @PathVariable Long id,
      @Valid @RequestBody AssetPriceUpdateRequest request) {
    return ResponseEntity.ok(assetService.updatePrice(userId, id, request.currentPrice()));
  }

  /**
   * 환율을 수동으로 갱신한다.
   *
   * @param userId 인증된 사용자 id
   * @param id 자산 id
   * @param request 새 환율
   * @return 갱신된 자산 정보
   */
  @Operation(summary = "환율 갱신", description = "해외주식의 환율은 MVP 에서 수동 입력이다. Transaction 을 만들지 않는다.")
  @PatchMapping("/{id}/exchange-rate")
  public ResponseEntity<AssetResponse> updateExchangeRate(
      @CurrentUserId Long userId,
      @PathVariable Long id,
      @Valid @RequestBody AssetExchangeRateUpdateRequest request) {
    return ResponseEntity.ok(assetService.updateExchangeRate(userId, id, request.exchangeRate()));
  }
}
