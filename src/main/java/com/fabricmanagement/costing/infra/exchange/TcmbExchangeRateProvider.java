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
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * TCMB (Türkiye Cumhuriyet Merkez Bankası) döviz kuru sağlayıcı.
 *
 * <p>Lazy cache pattern ile çalışır: İlk getRate() çağrısında TCMB XML feed'inden günlük kurları
 * çeker ve JVM-level in-memory cache'te saklar. Sonraki çağrılar cache'ten döner. Her tenant için
 * DB'ye audit trail kaydeder.
 *
 * <p>ManualExchangeRateProvider'dan sonra çalışır (@Order(2)). Manuel girilen kurlar her zaman
 * TCMB'yi override eder çünkü chain'de önce çözümlenir ve bu provider'a sıra gelmez.
 */
@Slf4j
@Component
@Order(2)
public class TcmbExchangeRateProvider implements ExchangeRateProvider {

  private static final int MAX_CACHE_DAYS = 30;

  private final ExchangeRateCacheRepository cacheRepo;

  // Global in-memory lazy cache: JVM level, Tenant agnostic
  // Structure: Date -> (Currency -> TRY Rate)
  // Empty map = fetch attempted but failed (prevents retry storm)
  private final Map<LocalDate, Map<String, BigDecimal>> tcmbDailyRates = new ConcurrentHashMap<>();

  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  public TcmbExchangeRateProvider(ExchangeRateCacheRepository cacheRepo) {
    this.cacheRepo = cacheRepo;
  }

  @Override
  public Optional<BigDecimal> getRate(UUID tenantId, String from, String to, LocalDate date) {
    if (from.equalsIgnoreCase(to)) {
      return Optional.of(BigDecimal.ONE);
    }

    evictStaleCacheEntries();

    // Lazy fetch: computeIfAbsent never receives null (we return emptyMap on failure)
    Map<String, BigDecimal> dayRates =
        tcmbDailyRates.computeIfAbsent(date, this::fetchRatesForDate);

    if (dayRates.isEmpty()) {
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
        return Collections.emptyMap();
      }

      Map<String, BigDecimal> parsed = parseXml(response.body());
      return parsed.isEmpty() ? Collections.emptyMap() : parsed;

    } catch (Exception e) {
      log.error("Failed to fetch TCMB rates from URL {}: {}", url, e.getMessage());
      return Collections.emptyMap();
    }
  }

  /**
   * Her zaman tarih bazlı URL kullanır. today.xml tutarsız format ve güncelleme zamanlaması
   * nedeniyle kullanılmaz.
   */
  String buildTcmbUrl(LocalDate date) {
    String yearMonth = date.format(DateTimeFormatter.ofPattern("yyyyMM"));
    String dayMonthYear = date.format(DateTimeFormatter.ofPattern("ddMMyyyy"));
    return String.format("https://www.tcmb.gov.tr/kurlar/%s/%s.xml", yearMonth, dayMonthYear);
  }

  private Map<String, BigDecimal> parseXml(InputStream is) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

    // XXE koruması — defense-in-depth
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

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
        if (forexBuying == null || forexBuying.isBlank()) {
          log.debug("Empty ForexBuying for currency: {}", currencyCode);
          continue;
        }
        BigDecimal rate = new BigDecimal(forexBuying.trim());
        if (rate.compareTo(BigDecimal.ZERO) > 0) {
          rates.put(currencyCode, rate);
        }
      } catch (NumberFormatException e) {
        log.debug("Skipping unparseable rate for currency: {}", currencyCode);
      }
    }
    return rates;
  }

  Optional<BigDecimal> calculateCrossRate(
      String from, String to, Map<String, BigDecimal> dayRates) {
    if (!dayRates.containsKey(from) || !dayRates.containsKey(to)) {
      return Optional.empty();
    }

    BigDecimal fromToTry = dayRates.get(from);
    BigDecimal toToTry = dayRates.get(to);

    // Cross-rate calculation: USD->EUR = USD->TRY / EUR->TRY
    // Using scale 6, RoundingMode.HALF_UP (NUMERIC(15,6) uyumlu)
    try {
      BigDecimal rate = fromToTry.divide(toToTry, 6, RoundingMode.HALF_UP);
      return Optional.of(rate);
    } catch (ArithmeticException e) {
      return Optional.empty();
    }
  }

  /** Mevcut kayıt varsa günceller, yoksa yeni kaydeder — tenant bazlı audit trail. */
  void saveToAuditCache(
      UUID tenantId, String base, String target, BigDecimal rate, LocalDate date) {
    ExchangeRateCache existing =
        cacheRepo
            .findFirstByTenantIdAndBaseCurrencyAndTargetCurrencyAndRateDateAndIsActiveTrue(
                tenantId, base, target, date)
            .orElse(null);

    if (existing != null) {
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

  /** 30 günden eski cache entry'lerini temizler — bellek hijyeni. */
  private void evictStaleCacheEntries() {
    LocalDate cutoff = LocalDate.now().minusDays(MAX_CACHE_DAYS);
    tcmbDailyRates.keySet().removeIf(date -> date.isBefore(cutoff));
  }
}
