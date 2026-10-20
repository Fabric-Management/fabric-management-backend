package com.fabricmanagement.common.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * FabricOS is a UK product, so its seed and demo data is British (root {@code AGENTS.md}, "FABRICOS
 * IS A UK PRODUCT"). This guard reads the seeders' source and the partner network data, so a
 * Turkish name, place, phone number or currency cannot come back unnoticed.
 */
class BritishSampleDataRuleTest {

  private static final Path SEEDERS =
      Path.of("src/main/java/com/fabricmanagement/common/infrastructure/bootstrap");

  /** Letters that exist in Turkish but not in German, French or Portuguese (partners abroad). */
  private static final Pattern TURKISH_LETTERS = Pattern.compile("[ğĞıİşŞ]");

  private static final List<String> TURKISH_SAMPLE_MARKERS =
      List.of(
          "A.Ş.",
          "Ltd. Şti",
          "Tekstil",
          "Konfeksiyon",
          "İstanbul",
          "Istanbul",
          "Ankara",
          "Izmir",
          "Bursa",
          "Anadolu",
          "Anatolia",
          "Aegean",
          "Marmara",
          "+90",
          ".com.tr");

  /** A Turkish country, tax-id prefix or currency used as sample data. */
  private static final Pattern TURKEY_AS_SAMPLE_DATA =
      Pattern.compile("\"TUR\"|\"TR\"|\"TRY\"|\"TR-");

  private static List<Path> seederSources() throws IOException {
    try (Stream<Path> files = Files.list(SEEDERS)) {
      return files.filter(file -> file.toString().endsWith(".java")).toList();
    }
  }

  @Test
  void noSeederSourceContainsTurkishSampleData() throws IOException {
    List<Path> sources = seederSources();
    assertThat(sources)
        .as("the seeders are found from the module directory")
        .hasSizeGreaterThan(10);
    for (Path source : sources) {
      String text = Files.readString(source);
      assertThat(TURKISH_LETTERS.matcher(text).find())
          .as("%s has Turkish letters; sample data must be British", source.getFileName())
          .isFalse();
      assertThat(TURKEY_AS_SAMPLE_DATA.matcher(text).find())
          .as("%s uses Turkey as a sample country, tax id or currency", source.getFileName())
          .isFalse();
      for (String marker : TURKISH_SAMPLE_MARKERS) {
        assertThat(text)
            .as("%s contains the Turkish sample marker '%s'", source.getFileName(), marker)
            .doesNotContain(marker);
      }
    }
  }

  @Test
  void thePartnerNetworkIsNotTurkishAndMostlyBritish() {
    var customers = PartnerNetworkDemoData.CUSTOMERS;
    assertThat(customers)
        .extracting(customer -> customer.place().alpha2())
        .doesNotContain("TR")
        .contains("GB");
    assertThat(customers.stream().filter(customer -> "GB".equals(customer.place().alpha2())))
        .as("the first customers are British; abroad is the exception")
        .hasSizeGreaterThanOrEqualTo(2);
    PartnerNetworkDemoData.SUPPLIERS.forEach(
        supplier -> {
          assertThat(supplier.contact().phone()).doesNotStartWith("+90");
          assertThat(supplier.contact().email()).doesNotEndWith(".tr");
        });
  }
}
