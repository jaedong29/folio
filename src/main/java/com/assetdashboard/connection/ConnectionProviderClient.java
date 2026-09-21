package com.assetdashboard.connection;

import com.assetdashboard.infra.price.fx.FxRateQueryService;
import com.assetdashboard.infra.price.fx.FxRateQuote;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class ConnectionProviderClient {
  private final RestClient http;
  private final FxRateQueryService fx;
  private final Clock clock;

  public ConnectionProviderClient(@Qualifier("connectionRestClient") RestClient http,
      FxRateQueryService fx, Clock clock) {
    this.http = http; this.fx = fx; this.clock = clock;
  }
  public record Result(String accountId, ConnectionSnapshot snapshot) {}

  public Result fetch(ConnectionProvider provider, ConnectionCredentials credentials, String accountId) {
    try {
      return provider == ConnectionProvider.BINANCE_SPOT ? binance(credentials) : toss(credentials, accountId);
    } catch (ConnectionFetchException e) { throw e; }
    catch (RestClientResponseException e) {
      int status = e.getStatusCode().value();
      throw new ConnectionFetchException(status == 401 || status == 403 ? "AUTH_OR_IP_REJECTED"
          : status == 429 || status == 418 ? "PROVIDER_RATE_LIMITED" : "PROVIDER_UNAVAILABLE");
    } catch (Exception e) { throw new ConnectionFetchException("PROVIDER_UNAVAILABLE"); }
  }

  private Result binance(ConnectionCredentials credentials) throws Exception {
    JsonNode permission = signedGet("/sapi/v1/account/apiRestrictions", credentials);
    if (!permission.path("enableReading").isBoolean() || !permission.path("enableReading").booleanValue()) {
      throw new ConnectionFetchException("READ_ONLY_KEY_REQUIRED");
    }
    // Fail closed for known order, transfer and withdrawal privileges.
    for (String field : List.of("enableWithdrawals", "enableInternalTransfer", "enableMargin",
        "enableFutures", "permitsUniversalTransfer", "enableVanillaOptions", "enableFixApiTrade",
        "enableSpotAndMarginTrading", "enablePortfolioMarginTrading")) {
      if (!permission.path(field).isBoolean() || permission.path(field).booleanValue()) {
        throw new ConnectionFetchException("READ_ONLY_KEY_REQUIRED");
      }
    }
    JsonNode account = signedGet("/api/v3/account", credentials);
    if (!"SPOT".equals(account.path("accountType").asText())) invalid();
    JsonNode balances = requireArray(account.path("balances"));
    List<JsonNode> positions = new ArrayList<>();
    Set<String> symbols = new HashSet<>();
    for (JsonNode item : balances) {
      String symbol = requiredText(item, "asset");
      if (!symbols.add(symbol)) invalid();
      BigDecimal total = nonnegative(item.path("free")).add(nonnegative(item.path("locked")));
      if (total.signum() > 0) positions.add(item);
    }
    Map<String, BigDecimal> prices = new HashMap<>();
    if (positions.stream().anyMatch(p -> !"USDT".equals(p.path("asset").asText()))) {
      // A price outage does not erase balances: unmatched prices remain explicitly unknown.
      try {
        JsonNode tickers = http.get().uri("https://api.binance.com/api/v3/ticker/price")
            .retrieve().body(JsonNode.class);
        if (tickers != null && tickers.isArray()) {
          for (JsonNode ticker : tickers) {
            BigDecimal value = optionalDecimal(ticker.path("price"));
            if (value != null && value.signum() > 0) prices.put(ticker.path("symbol").asText(), value);
          }
        }
      } catch (Exception ignored) { /* retain positions without inventing prices */ }
    }
    FxRateQuote rate = positions.isEmpty() ? null : fx.fetchWithFallback("USDT", false).orElse(null);
    List<ConnectedHolding> holdings = new ArrayList<>();
    for (JsonNode item : positions) {
      String symbol = requiredText(item, "asset");
      BigDecimal locked = nonnegative(item.path("locked"));
      BigDecimal quantity = nonnegative(item.path("free")).add(locked);
      BigDecimal price = "USDT".equals(symbol) ? BigDecimal.ONE : prices.get(symbol + "USDT");
      holdings.add(holding(symbol, symbol, "USDT".equals(symbol) ? "CASH" : "CRYPTO", "USDT",
          quantity, locked, price, null, null, price == null ? null : quantity.multiply(price), rate));
    }
    return new Result(null, new ConnectionSnapshot(clock.instant(), holdings));
  }

  private JsonNode signedGet(String path, ConnectionCredentials credentials) throws Exception {
    String query = "timestamp=" + clock.millis() + "&recvWindow=5000"
        + ("/api/v3/account".equals(path) ? "&omitZeroBalances=true" : "");
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(credentials.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    String signature = HexFormat.of().formatHex(mac.doFinal(query.getBytes(StandardCharsets.UTF_8)));
    JsonNode response = http.get().uri("https://api.binance.com" + path + "?" + query + "&signature=" + signature)
        .header("X-MBX-APIKEY", credentials.key()).retrieve().body(JsonNode.class);
    if (response == null || !response.isObject()) invalid();
    return response;
  }

  private Result toss(ConnectionCredentials credentials, String selectedAccount) {
    LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "client_credentials"); form.add("client_id", credentials.key());
    form.add("client_secret", credentials.secret());
    JsonNode auth = http.post().uri("https://openapi.tossinvest.com/oauth2/token")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(JsonNode.class);
    String token = requiredText(auth, "access_token", 8192);
    JsonNode accountsBody = http.get().uri("https://openapi.tossinvest.com/api/v1/accounts")
        .headers(h -> h.setBearerAuth(token)).retrieve().body(JsonNode.class);
    if (accountsBody == null) invalid();
    JsonNode accounts = requireArray(accountsBody.path("result"));
    if (accounts.isEmpty()) throw new ConnectionFetchException("NO_SUPPORTED_ACCOUNT");
    if (accounts.size() != 1 && selectedAccount == null) throw new ConnectionFetchException("MULTIPLE_ACCOUNTS_UNSUPPORTED");
    String accountId = selectedAccount;
    if (accountId == null) accountId = requiredText(accounts.get(0), "accountSeq");
    final String chosen = accountId;
    boolean exists = false;
    for (JsonNode account : accounts) {
      if (chosen.equals(requiredText(account, "accountSeq")) && "BROKERAGE".equals(account.path("accountType").asText())) exists = true;
    }
    if (!exists) throw new ConnectionFetchException("NO_SUPPORTED_ACCOUNT");
    JsonNode body = http.get().uri("https://openapi.tossinvest.com/api/v1/holdings")
        .headers(h -> h.setBearerAuth(token)).header("X-Tossinvest-Account", accountId)
        .retrieve().body(JsonNode.class);
    if (body == null) invalid();
    JsonNode items = requireArray(body.path("result").path("items"));
    Map<String, FxRateQuote> rates = new HashMap<>();
    Set<String> symbols = new HashSet<>();
    List<ConnectedHolding> holdings = new ArrayList<>();
    for (JsonNode item : items) {
      String symbol = requiredText(item, "symbol");
      String country = requiredText(item, "marketCountry");
      String currency = requiredText(item, "currency");
      if (!("KR".equals(country) && "KRW".equals(currency)) && !("US".equals(country) && "USD".equals(currency))) invalid();
      if (!symbols.add(country + ":" + symbol)) invalid();
      BigDecimal quantity = nonnegative(item.path("quantity"));
      if (quantity.signum() == 0) continue;
      if (!rates.containsKey(currency)) rates.put(currency, fx.fetchWithFallback(currency, false).orElse(null));
      BigDecimal price = optionalPositive(item.path("lastPrice"));
      BigDecimal average = optionalPositive(item.path("averagePurchasePrice"));
      BigDecimal value = optionalDecimal(item.path("marketValue").path("amount"));
      if (value != null && value.signum() < 0) invalid();
      holdings.add(holding(symbol, requiredText(item, "name"), "STOCK", currency, quantity, null,
          price, average, optionalDecimal(item.path("profitLoss").path("amount")), value, rates.get(currency)));
    }
    return new Result(accountId, new ConnectionSnapshot(clock.instant(), holdings));
  }

  private ConnectedHolding holding(String symbol, String name, String category, String currency,
      BigDecimal quantity, BigDecimal locked, BigDecimal price, BigDecimal average, BigDecimal pnl,
      BigDecimal value, FxRateQuote rate) {
    BigDecimal krwRate = "KRW".equals(currency) ? BigDecimal.ONE : rate == null ? null : rate.krwRate();
    return new ConnectedHolding(symbol, name, category, currency, quantity, locked, price, average, pnl,
        value, krwRate, rate == null ? null : rate.fetchedAt(), rate == null ? null : rate.source(),
        value == null || krwRate == null ? null : value.multiply(krwRate),
        !"KRW".equals(currency) && (rate == null || rate.isExpired(15)));
  }

  private static JsonNode requireArray(JsonNode node) {
    if (node == null || !node.isArray() || node.size() > 1000) invalid();
    return node;
  }
  private static String requiredText(JsonNode node, String field) {
    return requiredText(node, field, 200);
  }
  private static String requiredText(JsonNode node, String field, int maxLength) {
    if (node == null || !node.hasNonNull(field) || !node.get(field).isValueNode()
        || node.get(field).asText().isBlank() || node.get(field).asText().length() > maxLength) invalid();
    return node.get(field).asText();
  }
  private static BigDecimal optionalDecimal(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) return null;
    try {
      String text = node.asText();
      if (!text.matches("-?[0-9]{1,24}(\\.[0-9]{1,18})?")) { invalid(); }
      return new BigDecimal(text);
    } catch (NumberFormatException e) { throw new ConnectionFetchException("INVALID_PROVIDER_RESPONSE"); }
  }
  private static BigDecimal optionalPositive(JsonNode node) {
    BigDecimal value = optionalDecimal(node);
    if (value != null && value.signum() < 0) invalid();
    return value == null || value.signum() == 0 ? null : value;
  }
  private static BigDecimal nonnegative(JsonNode node) {
    BigDecimal value = optionalDecimal(node);
    if (value == null || value.signum() < 0) invalid();
    return value;
  }
  private static void invalid() { throw new ConnectionFetchException("INVALID_PROVIDER_RESPONSE"); }
}
