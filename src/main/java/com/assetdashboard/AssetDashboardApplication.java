package com.assetdashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Asset Dashboard 애플리케이션 진입점.
 *
 * <p>개인 자산(현금·은행·주식·암호화폐)을 한 화면에서 조회하는 서비스의 부트스트랩 클래스다.
 */
@EnableJpaAuditing
@ConfigurationPropertiesScan
@SpringBootApplication
public class AssetDashboardApplication {

  /**
   * 애플리케이션을 기동한다.
   *
   * @param args 커맨드라인 인자
   */
  public static void main(String[] args) {
    SpringApplication.run(AssetDashboardApplication.class, args);
  }
}
