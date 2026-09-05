package com.assetdashboard.domain.transaction.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assetdashboard.global.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Idempotency-Key가 DB에 도달하기 전에 HTTP 입력으로 검증되는지 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
class TransactionControllerIdempotencyKeyIntegrationTest {

  private static final String VALID_DEPOSIT_BODY =
      """
      {
        "quantity": 1,
        "exchangeRateMode": "AUTO",
        "tradedAt": "2026-09-05T00:00:00"
      }
      """;

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtTokenProvider jwtTokenProvider;

  @Test
  void rejectsBlankIdempotencyKeyAsInvalidInput() throws Exception {
    performDepositWithKey("   ")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
  }

  @Test
  void rejectsIdempotencyKeyLongerThanTheDatabaseColumnAsInvalidInput() throws Exception {
    performDepositWithKey("x".repeat(256))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
  }

  private org.springframework.test.web.servlet.ResultActions performDepositWithKey(String key)
      throws Exception {
    return mockMvc.perform(
        post("/api/assets/999999/transactions/deposit")
            .header("Authorization", "Bearer " + jwtTokenProvider.createToken(7L))
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_DEPOSIT_BODY));
  }
}
