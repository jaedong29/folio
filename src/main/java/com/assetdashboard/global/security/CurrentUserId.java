package com.assetdashboard.global.security;

import io.swagger.v3.oas.annotations.Parameter;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 인증된 사용자의 id 를 컨트롤러 파라미터로 주입받기 위한 표시.
 *
 * <p>PRD 4-0 규칙 4를 코드 수준에서 강제하기 위한 장치다. userId 를 요청 본문이나 쿼리 파라미터로 받을 수 있는
 * 통로를 아예 만들지 않고, 검증된 JWT(SecurityContext)에서만 꺼내게 한다.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Parameter(hidden = true)
public @interface CurrentUserId {}
