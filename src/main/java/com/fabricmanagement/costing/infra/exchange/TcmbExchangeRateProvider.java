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
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class TcmbExchangeRateProvider implements ExchangeRateProvider {

  private final ExchangeRateCacheRepository cacheRepo;

  // Global in-memory lazy cache: JVM level, Tenant agnostic
  // Structure: Date -> (Currency -> TRY Rate)
  private final Map<LocalDate, Map<String, BigDecimal>> tcmbDailyRates = new ConcurrentHashMap<>();

  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  @Override
  public Optional<BigDecimal> getRate(UUID tenantId, String from, String to, LocalDate date) {
    if (from.equalsIgnoreCase(to)) {
      return Optional.of(BigDecimal.ONE);
    }

    // Try fetching rates for the given date (lazy fetch)
    Map<String, BigDecimal> dayRates =
        tcmbDailyRates.computeIfAbsent(date, this::fetchRatesForDate);

    if (dayRates == null || dayRates.isEmpty()) {
      return Optional.empty(); // Network error, holiday, or failed parsing
    }

    Optional<BigDecimal> calculatedRate = calculateCrossRate(from, to, dayRates);

    // Write to audit trail for this tenant (only if rate was successfully found/calculated)
    calculatedRate.ifPresent(rate -> saveToAuditCache(tenantId, from, to, rate, date));

    return calculatedRate;
  }

  private Map<String, BigDecimal> fetchRatesForDate(LocalDate date) {
    String url = buildTcmbUrl(date);
    log.info("Fetching TCMB rates from: {}", url);

    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(Duration.ofSeconds(5))
              .GET()
              .build();

      HttpResponse<InputStream> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

      if (response.statusCode() != 200) {
        log.warn("TCMB returned status {} for URL: {}", response.statusCode(), url);
        return null;
      }

      return parseXml(response.body());

    } catch (Exception e) {
      log.error("Failed to fetch TCMB rates from URL {}: {}", url, e.getMessage());
      return null;
    }
  }

  private String buildTcmbUrl(LocalDate date) {
    LocalDate today = LocalDate.now();
    if (date.isEqual(today)) {
      return "https://www.tcmb.gov.tr/kurlar/today.xml";
    }

    String yearMonth = date.format(DateTimeFormatter.ofPattern("yyyyMM"));
    String dayMonthYear = date.format(DateTimeFormatter.ofPattern("ddMMyyyy"));
    return String.format("https://www.tcmb.gov.tr/kurlar/%s/%s.xml", yearMonth, dayMonthYear);
  }

  private Map<String, BigDecimal> parseXml(InputStream is) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    Document doc = builder.parse(is);
    doc.getDocumentElement().normalize();

    Map<String, BigDecimal> rates = new ConcurrentHashMap<>();
    rates.put("TRY", BigDecimal.ONE); // Base is TRY

    NodeList nList = doc.getElementsByTagName("Currency");
    for (int temp = 0; temp < nList.getLength(); temp++) {
      Element element = (Element) nList.item(temp);
      String currencyCode = element.getAttribute("Kod");

      if (currencyCode.equals("XDR")) continue; // Skip Special Drawing Rights

      String forexBuying = element.getElementsByTagName("ForexBuying").item(0).getTextContent();
      try {
        BigDecimal rate = new BigDecimal(forexBuying.trim());
        rates.put(currencyCode, rate);
      } catch (Exception e) {
        // Log ignoring specific parse issue and continue
        log.debug("Skipping unparseable rate for currency: {}", currencyCode);
      }
    }
    return rates; // Date -> Currency -> TRY representation
  }

  private Optional<BigDecimal> calculateCrossRate(
      String from, String to, Map<String, BigDecimal> dayRates) {
    if (!dayRates.containsKey(from) || !dayRates.containsKey(to)) {
      return Optional.empty(); // We don't have rates for one of them
    }

    BigDecimal fromToTry = dayRates.get(from);
    BigDecimal toToTry = dayRates.get(to);

    // Cross-rate calculation: USD->EUR = USD->TRY / EUR->TRY
    // Example: 38.5432 / 35.1234
    // Using scale 6, RoundingMode.HALF_UP
    try {
      BigDecimal rate = fromToTry.divide(toToTry, 6, RoundingMode.HALF_UP);
      return Optional.of(rate);
    } catch (ArithmeticException e) {
      return Optional.empty();
    }
  }

  private void saveToAuditCache(
      UUID tenantId, String base, String target, BigDecimal rate, LocalDate date) {
    ExchangeRateCache existing =
        cacheRepo
            .findFirstByTenantIdAndBaseCurrencyAndTargetCurrencyAndRateDateAndIsActiveTrue(
                tenantId, base, target, date)
            .orElse(null);

    if (existing != null) {
      if (existing.getSource() != ExchangeRateSource.TCMB) {
        // Overridden by manual means, do not touch!
        // Actually, if it's found in the provider sequence we know manual provider didn't catch it
        // Or if it did catch it, we wouldn't reach here because Manual gets executed first.
        return;
      }
      existing.setRate(rate);
      cacheRepo.save(existing);
    } else {
      ExchangeRateCache cache =
          ExchangeRateCache.builder()
              .baseCurrency(base)
              .targetCurrency(target)
              .rate(rate)
              .rateDate(date)
              .source(ExchangeRateSource.TCMB)
              .build();
      cache.setTenantId(tenantId);
      cacheRepo.save(cache);
    }
  }
}
