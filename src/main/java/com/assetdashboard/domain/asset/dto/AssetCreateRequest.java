package com.assetdashboard.domain.asset.dto;

import com.assetdashboard.domain.asset.entity.AssetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 자산 등록 요청 (PRD 4-2).
 *
 * @param type 자산 종류
 * @param symbol 시세 조회 키. 영문·숫자·{@code .} 만 허용하며 저장 시 대문자로 정규화된다
 * @param name 화면 표시용 이름. <b>선택 입력</b>이며 비우면 symbol 을 그대로 사용한다 (PRD 5장)
 * @param currency currentPrice 의 통화 코드
 */
public record AssetCreateRequest(
    @Schema(example = "CRYPTO") @NotNull(message = "자산 종류는 필수입니다.") AssetType type,
    @Schema(example = "BTC", description = "국내주식은 000660.KS / 000660.KQ 형식")
        @NotBlank(message = "심볼은 필수입니다.")
        @Size(max = 30, message = "심볼은 30자를 넘을 수 없습니다.")
        @Pattern(regexp = "^[A-Za-z0-9.]+$", message = "심볼은 영문·숫자·마침표만 사용할 수 있습니다.")
        String symbol,
    @Schema(example = "비트코인", description = "비우면 심볼이 표시 이름이 된다")
        @Size(max = 100, message = "이름은 100자를 넘을 수 없습니다.")
        String name,
    @Schema(example = "USDT")
        @NotBlank(message = "통화는 필수입니다.")
        @Size(max = 10, message = "통화 코드는 10자를 넘을 수 없습니다.")
        String currency) {

  /**
   * 저장에 사용할 정규화된 심볼을 반환한다.
   *
   * <p>{@code btc} 와 {@code BTC} 가 서로 다른 값으로 취급되면 {@code (user_id, type, symbol)} 유니크
   * 제약이 중복 등록을 막지 못한다(PRD 4-8).
   *
   * @return 대문자로 변환된 심볼
   */
  public String normalizedSymbol() {
    return symbol.trim().toUpperCase();
  }

  /**
   * 저장에 사용할 표시 이름을 반환한다.
   *
   * @return 입력된 이름, 비어 있으면 정규화된 심볼
   */
  public String resolvedName() {
    return (name == null || name.isBlank()) ? normalizedSymbol() : name.trim();
  }

  /**
   * 저장에 사용할 통화 코드를 반환한다.
   *
   * @return 대문자로 변환된 통화 코드
   */
  public String normalizedCurrency() {
    return currency.trim().toUpperCase();
  }
}
