package com.assetdashboard.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI 에서 Bearer 토큰을 입력할 수 있도록 OpenAPI 문서를 구성한다.
 *
 * <p>대부분의 API 가 인증을 요구하므로 전역 SecurityRequirement 를 걸어 Swagger 의 Authorize 버튼 하나로
 * 모든 요청에 토큰이 붙게 한다.
 */
@Configuration
public class OpenApiConfig {

  private static final String SCHEME_NAME = "bearerAuth";

  /**
   * API 메타데이터와 JWT 인증 스킴을 담은 OpenAPI 정의를 만든다.
   *
   * @return OpenAPI 정의
   */
  @Bean
  public OpenAPI assetDashboardOpenApi() {
    SecurityScheme bearer =
        new SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT");

    return new OpenAPI()
        .info(
            new Info()
                .title("Asset Dashboard API")
                .version("v1")
                .description("개인 자산(현금·은행·주식·암호화폐) 조회 대시보드 API"))
        .components(new Components().addSecuritySchemes(SCHEME_NAME, bearer))
        .addSecurityItem(new SecurityRequirement().addList(SCHEME_NAME));
  }
}
