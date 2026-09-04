package com.assetdashboard.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** 인증 컨텍스트에서 주입되는 userId가 Swagger 요청 파라미터로 노출되지 않게 고정한다. */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiCurrentUserIdTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void hidesCurrentUserIdFromSwaggerOperations() throws Exception {
    String document =
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode paths = objectMapper.readTree(document).path("paths");

    boolean exposesUserId =
        StreamSupport.stream(paths.spliterator(), false)
            .flatMap(path -> StreamSupport.stream(path.spliterator(), false))
            .flatMap(
                operation ->
                    StreamSupport.stream(operation.path("parameters").spliterator(), false))
            .anyMatch(parameter -> "userId".equals(parameter.path("name").asText()));

    assertThat(exposesUserId).isFalse();
  }
}
