package com.assetdashboard.domain.asset.dto;

/**
 * 자산 등록 결과. 새로 만들었는지 삭제된 자산을 되살렸는지를 컨트롤러에 알린다.
 *
 * <p>PRD 4-2 가 정의한 응답 본문 형태를 바꾸지 않으면서 두 경우를 구분하기 위해, 본문에 필드를 추가하는 대신 HTTP
 * 상태 코드(201 신규 / 200 복구)로 차이를 표현한다.
 *
 * @param asset 등록되었거나 복구된 자산
 * @param restored 삭제되었던 자산을 되살린 경우 {@code true}
 */
public record AssetCreationResult(AssetResponse asset, boolean restored) {}
