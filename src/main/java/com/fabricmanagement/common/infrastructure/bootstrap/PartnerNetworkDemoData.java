package com.fabricmanagement.common.infrastructure.bootstrap;

import com.fabricmanagement.platform.communication.domain.AddressType;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerContactRole;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerLinkRole;
import java.util.List;

/**
 * The people, address cards and linked companies a playground tenant's customers and suppliers come
 * with (PLAYGROUND-PARTIES-1). Plain data: {@link PartnerNetworkDemoSeeder} writes it through the
 * services the screens use.
 *
 * <p>Every e-mail address is on a reserved {@code .example} domain, so nothing seeded can reach a
 * stranger. In a sandboxed tenant every message is redirected to the address the prospect
 * registered with anyway (EMAIL-SANDBOX-1), and the subject names the contact it would have
 * reached. Phone numbers come from the fictional ranges the numbering plans reserve for drama.
 *
 * <p>Verification is mixed on purpose: one company and two address cards are left unverified, so
 * the "planning must verify" notice and the verified label both show on the order form.
 */
final class PartnerNetworkDemoData {

  private PartnerNetworkDemoData() {}

  /** A country as the two services want it: alpha-3 for companies, alpha-2 for address cards. */
  record Place(String alpha3, String alpha2, String name) {}

  record ContactSpec(String name, PartnerContactRole role, String email, String phone) {}

  record CardSpec(
      String label,
      AddressType type,
      String street,
      String line2,
      String city,
      String region,
      String postalCode,
      boolean primary,
      boolean headquarters,
      boolean verified) {}

  record LinkedSpec(
      String name,
      String taxPrefix,
      Place place,
      PartnerLinkRole role,
      String note,
      boolean verified,
      List<ContactSpec> contacts,
      List<CardSpec> cards) {}

  record CustomerSpec(
      String name,
      Place place,
      List<ContactSpec> contacts,
      List<CardSpec> cards,
      List<LinkedSpec> linked) {}

  record SupplierSpec(String name, ContactSpec contact) {}

  static final Place GB = new Place("GBR", "GB", "United Kingdom");
  static final Place TR = new Place("TUR", "TR", "Türkiye");
  static final Place DE = new Place("DEU", "DE", "Germany");
  static final Place US = new Place("USA", "US", "United States");
  static final Place PT = new Place("PRT", "PT", "Portugal");
  static final Place BD = new Place("BGD", "BD", "Bangladesh");

  /** Forwarder used by two customers, so the tenant-wide company search finds one record. */
  static final LinkedSpec CHANNEL_FREIGHT =
      new LinkedSpec(
          "Channel Freight Ltd",
          "GB-CHF",
          GB,
          PartnerLinkRole.FORWARDER,
          "Consolidates and forwards export parcels",
          true,
          List.of(
              contact(
                  "Mark Doyle",
                  PartnerContactRole.LOGISTICS,
                  "mark.doyle@channel-freight.example",
                  "+44 1304 496 0142")),
          List.of(
              card(
                  "Dover depot",
                  AddressType.SHIPPING,
                  "Unit 3, Eastern Docks Road",
                  null,
                  "Dover",
                  "Kent",
                  "CT16 1JA",
                  true,
                  true,
                  true)));

  static final List<CustomerSpec> CUSTOMERS =
      List.of(
          new CustomerSpec(
              "Albion Apparel Ltd",
              GB,
              List.of(
                  contact(
                      "Olivia Clarke",
                      PartnerContactRole.BUYER,
                      "olivia.clarke@albion-apparel.example",
                      "+44 113 496 0101"),
                  contact(
                      "Tom Hughes",
                      PartnerContactRole.FINANCE,
                      "tom.hughes@albion-apparel.example",
                      "+44 113 496 0102"),
                  contact(
                      "Priya Shah",
                      PartnerContactRole.LOGISTICS,
                      "priya.shah@albion-apparel.example",
                      "+44 1924 496 0103")),
              List.of(
                  card(
                      "Head office, Leeds",
                      AddressType.HEADQUARTERS,
                      "14 Wellington Street",
                      "Floor 3",
                      "Leeds",
                      "West Yorkshire",
                      "LS1 4DL",
                      true,
                      true,
                      true),
                  card(
                      "Distribution centre, Wakefield",
                      AddressType.SHIPPING,
                      "Unit 6, Calder Park Trade Estate",
                      null,
                      "Wakefield",
                      "West Yorkshire",
                      "WF4 3QZ",
                      false,
                      false,
                      true)),
              List.of(
                  new LinkedSpec(
                      "Midlands Garment Makers Ltd",
                      "GB-MGM",
                      GB,
                      PartnerLinkRole.GARMENT_MAKER,
                      "Cuts and sews Albion's woven range",
                      true,
                      List.of(
                          contact(
                              "Gareth Price",
                              PartnerContactRole.LOGISTICS,
                              "gareth.price@midlands-garment.example",
                              "+44 116 496 0111")),
                      List.of(
                          card(
                              "Goods in, Leicester",
                              AddressType.SHIPPING,
                              "22 Abbey Lane Industrial Estate",
                              null,
                              "Leicester",
                              "Leicestershire",
                              "LE4 5RT",
                              true,
                              true,
                              true))),
                  new LinkedSpec(
                      "Northern Dyeworks Ltd",
                      "GB-NDW",
                      GB,
                      PartnerLinkRole.DYEHOUSE,
                      "Piece dyeing for Albion's navy and ecru",
                      false,
                      List.of(
                          contact(
                              "Helen Marsh",
                              PartnerContactRole.QUALITY,
                              "helen.marsh@northern-dyeworks.example",
                              "+44 1274 496 0121")),
                      List.of(
                          card(
                              "Dyehouse, Bradford",
                              AddressType.SHIPPING,
                              "Mill Lane Works",
                              null,
                              "Bradford",
                              "West Yorkshire",
                              "BD4 8PL",
                              true,
                              true,
                              false))),
                  CHANNEL_FREIGHT)),
          new CustomerSpec(
              "Anadolu Tekstil A.Ş.",
              TR,
              List.of(
                  contact(
                      "Ayşe Yılmaz",
                      PartnerContactRole.BUYER,
                      "ayse.yilmaz@anadolu-tekstil.example",
                      "+90 212 555 0101"),
                  contact(
                      "Mehmet Demir",
                      PartnerContactRole.FINANCE,
                      "mehmet.demir@anadolu-tekstil.example",
                      "+90 212 555 0102"),
                  contact(
                      "Ali Kaya",
                      PartnerContactRole.LOGISTICS,
                      "ali.kaya@anadolu-tekstil.example",
                      "+90 212 555 0103")),
              List.of(
                  card(
                      "Merkez ofis, Bağcılar",
                      AddressType.HEADQUARTERS,
                      "Atatürk Caddesi No: 45",
                      "Kat 4",
                      "İstanbul",
                      "Bağcılar",
                      "34200",
                      true,
                      true,
                      true),
                  card(
                      "Depo, Esenyurt",
                      AddressType.SHIPPING,
                      "Organize Sanayi Bölgesi 3. Cadde No: 12",
                      null,
                      "İstanbul",
                      "Esenyurt",
                      "34510",
                      false,
                      false,
                      false)),
              List.of(
                  new LinkedSpec(
                      "Çorlu Konfeksiyon Sanayi",
                      "TR-CKS",
                      TR,
                      PartnerLinkRole.GARMENT_MAKER,
                      "Dikim atölyesi",
                      true,
                      List.of(
                          contact(
                              "Hakan Öztürk",
                              PartnerContactRole.LOGISTICS,
                              "hakan.ozturk@corlu-konfeksiyon.example",
                              "+90 282 555 0111")),
                      List.of(
                          card(
                              "Fabrika, Çorlu",
                              AddressType.SHIPPING,
                              "Çorlu Sanayi Sitesi 14. Sokak No: 8",
                              null,
                              "Çorlu",
                              "Tekirdağ",
                              "59850",
                              true,
                              true,
                              true))),
                  new LinkedSpec(
                      "Bursa Baskı Atölyesi",
                      "TR-BBA",
                      TR,
                      PartnerLinkRole.PRINTER,
                      "Dijital ve rotasyon baskı",
                      true,
                      List.of(
                          contact(
                              "Selin Arslan",
                              PartnerContactRole.OTHER,
                              "selin.arslan@bursa-baski.example",
                              "+90 224 555 0121")),
                      List.of(
                          card(
                              "Baskı atölyesi, Bursa",
                              AddressType.SHIPPING,
                              "Demirtaş OSB Mah. Sanayi Cad. No: 27",
                              null,
                              "Bursa",
                              "Osmangazi",
                              "16110",
                              true,
                              true,
                              true))),
                  new LinkedSpec(
                      "Ege Lojistik A.Ş.",
                      "TR-EGL",
                      TR,
                      PartnerLinkRole.FORWARDER,
                      "İhracat sevkiyatları",
                      true,
                      List.of(
                          contact(
                              "Murat Şahin",
                              PartnerContactRole.LOGISTICS,
                              "murat.sahin@ege-lojistik.example",
                              "+90 232 555 0131")),
                      List.of(
                          card(
                              "Antrepo, İzmir",
                              AddressType.SHIPPING,
                              "Alsancak Liman Arkası No: 5",
                              null,
                              "İzmir",
                              "Konak",
                              "35220",
                              true,
                              true,
                              true))))),
          new CustomerSpec(
              "Europa Mode GmbH",
              DE,
              List.of(
                  contact(
                      "Julia Becker",
                      PartnerContactRole.BUYER,
                      "julia.becker@europa-mode.example",
                      "+49 211 555 0101"),
                  contact(
                      "Lukas Weber",
                      PartnerContactRole.QUALITY,
                      "lukas.weber@europa-mode.example",
                      "+49 211 555 0102")),
              List.of(
                  card(
                      "Hauptsitz, Düsseldorf",
                      AddressType.HEADQUARTERS,
                      "Königsallee 58",
                      null,
                      "Düsseldorf",
                      "Nordrhein-Westfalen",
                      "40212",
                      true,
                      true,
                      true),
                  card(
                      "Lager, Mönchengladbach",
                      AddressType.SHIPPING,
                      "Gewerbepark Nord 9",
                      null,
                      "Mönchengladbach",
                      "Nordrhein-Westfalen",
                      "41066",
                      false,
                      false,
                      true)),
              List.of(
                  new LinkedSpec(
                      "Porto Confecções Lda",
                      "PT-PCL",
                      PT,
                      PartnerLinkRole.GARMENT_MAKER,
                      "Sews the autumn knitwear programme",
                      true,
                      List.of(
                          contact(
                              "Inês Carvalho",
                              PartnerContactRole.LOGISTICS,
                              "ines.carvalho@porto-confeccoes.example",
                              "+351 22 555 0111")),
                      List.of(
                          card(
                              "Armazém, Guimarães",
                              AddressType.SHIPPING,
                              "Zona Industrial de Creixomil, Lote 17",
                              null,
                              "Guimarães",
                              "Braga",
                              "4800-045",
                              true,
                              true,
                              true))),
                  new LinkedSpec(
                      "Rhein Logistik GmbH",
                      "DE-RHL",
                      DE,
                      PartnerLinkRole.WAREHOUSE,
                      "Holds finished goods before the shops",
                      true,
                      List.of(
                          contact(
                              "Stefan Krüger",
                              PartnerContactRole.LOGISTICS,
                              "stefan.krueger@rhein-logistik.example",
                              "+49 203 555 0121")),
                      List.of(
                          card(
                              "Lager, Duisburg",
                              AddressType.SHIPPING,
                              "Hafenstraße 31",
                              null,
                              "Duisburg",
                              "Nordrhein-Westfalen",
                              "47119",
                              true,
                              true,
                              true))))),
          new CustomerSpec(
              "Global Fashion Wear Corp.",
              US,
              List.of(
                  contact(
                      "Sarah Mitchell",
                      PartnerContactRole.BUYER,
                      "sarah.mitchell@global-fashion-wear.example",
                      "+1 212 555 0101"),
                  contact(
                      "David Chen",
                      PartnerContactRole.FINANCE,
                      "david.chen@global-fashion-wear.example",
                      "+1 212 555 0102")),
              List.of(
                  card(
                      "Office, New York",
                      AddressType.HEADQUARTERS,
                      "350 Fifth Avenue",
                      "Suite 4100",
                      "New York",
                      "NY",
                      "10118",
                      true,
                      true,
                      true),
                  card(
                      "Distribution centre, Secaucus",
                      AddressType.SHIPPING,
                      "400 Meadowlands Parkway",
                      null,
                      "Secaucus",
                      "NJ",
                      "07094",
                      false,
                      false,
                      true)),
              List.of(
                  new LinkedSpec(
                      "Dhaka Apparel Works Ltd",
                      "BD-DAW",
                      BD,
                      PartnerLinkRole.GARMENT_MAKER,
                      "Basics programme",
                      true,
                      List.of(
                          contact(
                              "Rahim Chowdhury",
                              PartnerContactRole.LOGISTICS,
                              "rahim.chowdhury@dhaka-apparel.example",
                              "+880 2 555 0111")),
                      List.of(
                          card(
                              "Factory, Dhaka",
                              AddressType.SHIPPING,
                              "Plot 14, Road 5, Export Processing Zone",
                              null,
                              "Dhaka",
                              "Dhaka",
                              "1349",
                              true,
                              true,
                              true))),
                  CHANNEL_FREIGHT)));

  /** One purchasing contact per supplier the other demos create (role {@code OTHER}). */
  static final List<SupplierSpec> SUPPLIERS =
      List.of(
          supplier(
              "Ege Pamuk Kooperatifi",
              "Fatma Aydın",
              "fatma.aydin@ege-pamuk.example",
              "+90 232 555 0201"),
          supplier(
              "Yarn Source Ltd.",
              "Robert Hale",
              "robert.hale@yarn-source.example",
              "+1 704 555 0202"),
          supplier(
              "Dye & Chem BV", "Daan de Vries", "daan.devries@dye-chem.example", "+31 20 555 0203"),
          supplier(
              "Anatolia Cotton Co.",
              "Emre Tunç",
              "emre.tunc@anatolia-cotton.example",
              "+90 322 555 0204"),
          supplier(
              "Aegean Yarn Mills",
              "Zeynep Koç",
              "zeynep.koc@aegean-yarn.example",
              "+90 232 555 0205"),
          supplier(
              "Marmara Dye & Finish",
              "Burak Yıldız",
              "burak.yildiz@marmara-dye.example",
              "+90 212 555 0206"));

  private static ContactSpec contact(
      String name, PartnerContactRole role, String email, String phone) {
    return new ContactSpec(name, role, email, phone);
  }

  private static SupplierSpec supplier(String company, String name, String email, String phone) {
    return new SupplierSpec(company, contact(name, PartnerContactRole.OTHER, email, phone));
  }

  private static CardSpec card(
      String label,
      AddressType type,
      String street,
      String line2,
      String city,
      String region,
      String postalCode,
      boolean primary,
      boolean headquarters,
      boolean verified) {
    return new CardSpec(
        label, type, street, line2, city, region, postalCode, primary, headquarters, verified);
  }
}
