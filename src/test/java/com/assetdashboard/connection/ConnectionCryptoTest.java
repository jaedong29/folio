package com.assetdashboard.connection;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ConnectionCryptoTest {
  private final ConnectionCrypto crypto = new ConnectionCrypto(new ConnectionProperties(true,
      "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", 300), new ObjectMapper());
  private final ConnectionCredentials credentials = new ConnectionCredentials("fixture-key", "fixture-secret");

  @Test void encryptsWithDifferentNoncesAndBindsCiphertextToOwnerAndProvider() {
    String ciphertext = crypto.encrypt(1L, ConnectionProvider.TOSS, credentials);
    assertThat(ciphertext).doesNotContain("fixture").isNotEqualTo(crypto.encrypt(1L, ConnectionProvider.TOSS, credentials));
    assertThat(crypto.decrypt(1L, ConnectionProvider.TOSS, ciphertext)).isEqualTo(credentials);
    assertThatThrownBy(() -> crypto.decrypt(2L, ConnectionProvider.TOSS, ciphertext)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> crypto.decrypt(1L, ConnectionProvider.BINANCE_SPOT, ciphertext)).isInstanceOf(IllegalStateException.class);
  }
  @Test void refusesToStartEnabledWithoutASeparateEncryptionKey() {
    assertThatThrownBy(() -> new ConnectionCrypto(new ConnectionProperties(true, "", 300), new ObjectMapper()))
        .isInstanceOf(IllegalStateException.class);
    assertThat(credentials.toString()).doesNotContain("fixture");
  }
}
