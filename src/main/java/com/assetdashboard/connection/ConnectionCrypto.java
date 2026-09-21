package com.assetdashboard.connection;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** Random IV and owner/provider-bound authenticated encryption for connection secrets. */
@Component
public class ConnectionCrypto {
  private final SecretKeySpec key;
  private final ObjectMapper mapper;
  private final SecureRandom random = new SecureRandom();

  public ConnectionCrypto(ConnectionProperties properties, ObjectMapper mapper) {
    this.mapper = mapper;
    byte[] bytes;
    try { bytes = Base64.getDecoder().decode(properties.encryptionKey()); }
    catch (IllegalArgumentException e) { throw new IllegalStateException("Invalid connection encryption key"); }
    if (bytes.length != 32 && (properties.enabled() || bytes.length != 0)) {
      throw new IllegalStateException("APP_CONNECTIONS_ENCRYPTION_KEY must be Base64 of 32 random bytes");
    }
    key = bytes.length == 32 ? new SecretKeySpec(bytes, "AES") : null;
  }

  public String encrypt(Long userId, ConnectionProvider provider, ConnectionCredentials credentials) {
    try {
      byte[] iv = new byte[12];
      random.nextBytes(iv);
      Cipher cipher = cipher(Cipher.ENCRYPT_MODE, userId, provider, iv);
      byte[] data = cipher.doFinal(mapper.writeValueAsBytes(credentials));
      return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + data.length).put(iv).put(data).array());
    } catch (Exception e) { throw new IllegalStateException("Could not encrypt connection credentials"); }
  }

  public ConnectionCredentials decrypt(Long userId, ConnectionProvider provider, String encrypted) {
    try {
      ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(encrypted));
      byte[] iv = new byte[12]; buffer.get(iv);
      byte[] data = new byte[buffer.remaining()]; buffer.get(data);
      return mapper.readValue(cipher(Cipher.DECRYPT_MODE, userId, provider, iv).doFinal(data), ConnectionCredentials.class);
    } catch (Exception e) { throw new IllegalStateException("Could not decrypt connection credentials"); }
  }

  private Cipher cipher(int mode, Long userId, ConnectionProvider provider, byte[] iv) throws Exception {
    if (key == null) throw new IllegalStateException("Connection encryption is not configured");
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(mode, key, new GCMParameterSpec(128, iv));
    cipher.updateAAD(("folio-connection-v1:" + userId + ":" + provider).getBytes(StandardCharsets.UTF_8));
    return cipher;
  }
}
