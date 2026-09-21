package com.assetdashboard.connection;

import com.assetdashboard.global.security.CurrentUserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/connections")
@RequiredArgsConstructor
public class ConnectionController {
  private final ConnectionLifecycleService lifecycle;
  private final ConnectedPortfolioService portfolio;

  public record ConnectRequest(
      @NotBlank @Size(min = 8, max = 256) @Pattern(regexp = "[A-Za-z0-9_-]+", message = "키 형식을 확인해주세요.") String key,
      @NotBlank @Size(min = 8, max = 256) @Pattern(regexp = "[A-Za-z0-9_-]+", message = "시크릿 형식을 확인해주세요.") String secret) {
    @Override public String toString() { return "ConnectRequest[REDACTED]"; }
  }

  @GetMapping
  public ResponseEntity<ConnectedPortfolioService.Overview> overview(@CurrentUserId Long userId) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(portfolio.overview(userId));
  }
  @PutMapping("/{provider}")
  public ResponseEntity<Void> connect(@CurrentUserId Long userId, @PathVariable ConnectionProvider provider,
      @Valid @RequestBody ConnectRequest request) {
    lifecycle.connect(userId, provider, new ConnectionCredentials(request.key(), request.secret()));
    return ResponseEntity.accepted().build();
  }
  @PostMapping("/{id}/sync")
  public ResponseEntity<Void> sync(@CurrentUserId Long userId, @PathVariable Long id) {
    lifecycle.queue(userId, id);
    return ResponseEntity.accepted().build();
  }
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> disconnect(@CurrentUserId Long userId, @PathVariable Long id) {
    lifecycle.disconnect(userId, id);
    return ResponseEntity.noContent().build();
  }
}
