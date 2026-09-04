package com.assetdashboard.global.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 운영 코드와 테스트가 같은 시간 경계를 주입받게 한다. */
@Configuration
public class TimeConfig {

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
