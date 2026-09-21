package com.assetdashboard.connection;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConnectionSyncWorker {
  private final ConnectionLifecycleService lifecycle;
  private final ConnectionCrypto crypto;
  private final ConnectionProviderClient client;

  @Scheduled(scheduler = "connectionScheduler", fixedDelayString = "${app.connections.worker-delay-millis:3000}",
      initialDelayString = "${app.connections.worker-initial-delay-millis:3000}")
  public void processNext() {
    lifecycle.claimNext().ifPresent(claim -> {
      try {
        ConnectionCredentials credentials = crypto.decrypt(claim.userId(), claim.provider(), claim.encrypted());
        lifecycle.complete(claim, client.fetch(claim.provider(), credentials, claim.externalAccountId()));
      } catch (ConnectionFetchException e) {
        lifecycle.fail(claim, e.getMessage());
        log.warn("Connection sync failed id={} code={}", claim.id(), e.getMessage());
      } catch (Exception e) {
        lifecycle.fail(claim, "SYNC_FAILED");
        log.warn("Connection sync failed id={}", claim.id());
      }
    });
  }
}
