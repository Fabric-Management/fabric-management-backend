package com.fabricmanagement.costing.infra.exchange;

import com.fabricmanagement.costing.domain.exchange.ExchangeRateCache;
import com.fabricmanagement.costing.domain.exchange.ExchangeRateProvider;
import com.fabricmanagement.costing.domain.exchange.ExchangeRateSource;
import com.fabricmanagement.costing.infra.repository.ExchangeRateCacheRepository;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * ECB (European Central Bank) exchange rate provider.
 *
 * <p>Uses a lazy cache pattern: On the first getRate() call, fetches daily or 90-day rates from the
 * ECB XML feed and stores them in a JVM-level in-memory cache.
 *
 * <p>Note: ECB hist-90d boundary means requests older than 90 days will not find a rate in the
 * feed. For such deep historical recalculations, a MANUAL rate must be provided.
 */
@Slf4j
@Component
@Order(20)
@ConditionalOnProperty(
    prefix = "costing.fx.ecb",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class EcbExchangeRateProvider implements ExchangeRateProvider {

  private static final int MAX_CACHE_DAYS = 30;

  private final ExchangeRateCacheRepository cacheRepo;

  // Global in-memory lazy cache: JVM level, Tenant agnostic
  // Structure: Date -> (Currency -> Units per 1 EUR)
  private final Map<LocalDate, Map<String, BigDecimal>> ecbDailyRates = new ConcurrentHashMap<>();

  private final HttpClient httpClient;
  private final int timeoutSeconds;

  public EcbExchangeRateProvider(
      ExchangeRateCacheRepository cacheRepo,
      @Value("${costing.fx.ecb.timeout-seconds:5}") int timeoutSeconds) {
    this.cacheRepo = cacheRepo;
    this.timeoutSeconds = timeoutSeconds;
    this.httpClient =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeoutSeconds)).build();
  }

  @Override
  public Optional<BigDecimal> getRate(UUID tenantId, String from, String to, LocalDate date) {
    if (from.equalsIgnoreCase(to)) {
      return Optional.of(BigDecimal.ONE);
    }

    evictStaleCacheEntries();

    Map<String, BigDecimal> dayRates = ecbDailyRates.get(date);
    if (dayRates == null) {
      dayRates = fetchRatesForDate(date);
      if (!dayRates.isEmpty()) {
        ecbDailyRates.put(date, dayRates);
      }
    }

    if (dayRates.isEmpty()) {
      return Optional.empty(); // Network error, holiday, or failed parsing
    }

    Optional<BigDecimal> calculatedRate = calculateCrossRate(from, to, dayRates);

    // Persist to audit cache (skip if an active row exists to respect MANUAL precedence)
    calculatedRate.ifPresent(rate -> saveToAuditCache(tenantId, from, to, rate, date));

    return calculatedRate;
  }

  // Visible for testing (spy-based stubbing avoids spinning up an HTTP server)
  Map<String, BigDecimal> fetchRatesForDate(LocalDate date) {
    String url = buildEcbUrl(date);
    log.info("Fetching ECB rates from: {}", url);

    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(Duration.ofSeconds(timeoutSeconds))
              .GET()
              .build();

      HttpResponse<InputStream> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

      if (response.statusCode() != 200) {
        log.warn("ECB returned status {} for URL: {}", response.statusCode(), url);
        return Collections.emptyMap();
      }

      Map<String, BigDecimal> parsed = parseXml(response.body(), date);
      return parsed.isEmpty() ? Collections.emptyMap() : parsed;

    } catch (Exception e) {
      log.error("Failed to fetch ECB rates from URL {}: {}", url, e.getMessage());
      return Collections.emptyMap();
    }
  }

  String buildEcbUrl(LocalDate date) {
    if (date.isEqual(LocalDate.now())) {
      return "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml";
    } else {
      return "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-hist-90d.xml";
    }
  }

  Map<String, BigDecimal> parseXml(InputStream is, LocalDate targetDate) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

    DocumentBuilder builder = factory.newDocumentBuilder();
    Document doc = builder.parse(is);
    doc.getDocumentElement().normalize();

    Map<String, BigDecimal> rates = new ConcurrentHashMap<>();

    NodeList timeNodes = doc.getElementsByTagName("Cube");
    Element targetTimeElement = null;

    // Find the Cube with the matching time attribute (if available)
    for (int i = 0; i < timeNodes.getLength(); i++) {
      Element el = (Element) timeNodes.item(i);
      if (el.hasAttribute("time")) {
        LocalDate cubeDate = LocalDate.parse(el.getAttribute("time"));
        if (cubeDate.isEqual(targetDate)) {
          targetTimeElement = el;
          break;
        }
      }
    }

    if (targetTimeElement == null) {
      log.debug("No ECB rate data found for exact date: {}", targetDate);
      return rates;
    }

    rates.put("EUR", BigDecimal.ONE); // Base is EUR

    NodeList rateNodes = targetTimeElement.getElementsByTagName("Cube");
    for (int j = 0; j < rateNodes.getLength(); j++) {
      Element element = (Element) rateNodes.item(j);
      if (element.hasAttribute("currency") && element.hasAttribute("rate")) {
        String currencyCode = element.getAttribute("currency");
        try {
          BigDecimal rate = new BigDecimal(element.getAttribute("rate").trim());
          if (rate.compareTo(BigDecimal.ZERO) > 0) {
            rates.put(currencyCode, rate);
          }
        } catch (NumberFormatException e) {
          log.debug("Skipping unparseable rate for currency: {}", currencyCode);
        }
      }
    }
    return rates;
  }

  Optional<BigDecimal> calculateCrossRate(
      String from, String to, Map<String, BigDecimal> dayRates) {
    if (!dayRates.containsKey(from) || !dayRates.containsKey(to)) {
      return Optional.empty();
    }

    BigDecimal eurToFrom = dayRates.get(from);
    BigDecimal eurToTo = dayRates.get(to);

    // Cross-rate calculation: USD->GBP = rate(EUR->GBP) / rate(EUR->USD)
    // Using scale 6, RoundingMode.HALF_UP
    try {
      BigDecimal rate = eurToTo.divide(eurToFrom, 6, RoundingMode.HALF_UP);
      return Optional.of(rate);
    } catch (ArithmeticException e) {
      return Optional.empty();
    }
  }

  void saveToAuditCache(
      UUID tenantId, String base, String target, BigDecimal rate, LocalDate date) {
    ExchangeRateCache existing =
        cacheRepo
            .findFirstByTenantIdAndBaseCurrencyAndTargetCurrencyAndRateDateAndIsActiveTrue(
                tenantId, base, target, date)
            .orElse(null);

    if (existing != null) {
      // R1: Skip if active row exists (e.g. MANUAL rate) to maintain determinism.
      return;
    }

    ExchangeRateCache cache =
        ExchangeRateCache.builder()
            .baseCurrency(base)
            .targetCurrency(target)
            .rate(rate)
            .rateDate(date)
            .source(ExchangeRateSource.ECB)
            .build();
    cache.setTenantId(tenantId);
    cacheRepo.save(cache);
  }

  private void evictStaleCacheEntries() {
    LocalDate cutoff = LocalDate.now().minusDays(MAX_CACHE_DAYS);
    ecbDailyRates.keySet().removeIf(date -> date.isBefore(cutoff));
  }
}
