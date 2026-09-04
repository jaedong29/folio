package com.assetdashboard.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.assetdashboard.domain.transaction.dto.TradeRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;

class GlobalExceptionHandlerTest {

  @Test
  void unknownJsonFieldIsNamedInErrorResponse() throws Exception {
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    UnrecognizedPropertyException cause = null;
    try {
      mapper.readValue(
          """
          {
            "quantity": 1,
            "price": 100,
            "initialQuantity": 0.5,
            "settlementAssetId": 7,
            "tradedAt": "2026-08-01T10:00:00"
          }
          """,
          TradeRequest.class);
    } catch (UnrecognizedPropertyException e) {
      cause = e;
    }

    assertThat(cause).isNotNull();
    MockHttpInputMessage input = new MockHttpInputMessage(new byte[0]);
    input.getHeaders().putAll(HttpHeaders.EMPTY);
    HttpMessageNotReadableException requestException =
        new HttpMessageNotReadableException("invalid", cause, input);

    ResponseEntity<ErrorResponse> response =
        new GlobalExceptionHandler().handleUnreadable(requestException);

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("INVALID_INPUT");
    assertThat(response.getBody().message())
        .contains("initialQuantity", "exchangeRateMode", "quantity");
  }
}
