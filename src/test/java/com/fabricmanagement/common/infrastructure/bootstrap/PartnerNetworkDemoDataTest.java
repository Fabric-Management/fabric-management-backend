package com.fabricmanagement.common.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.bootstrap.PartnerNetworkDemoData.CardSpec;
import com.fabricmanagement.common.infrastructure.bootstrap.PartnerNetworkDemoData.ContactSpec;
import com.fabricmanagement.common.infrastructure.bootstrap.PartnerNetworkDemoData.CustomerSpec;
import com.fabricmanagement.common.infrastructure.bootstrap.PartnerNetworkDemoData.LinkedSpec;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerContactRole;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** The shape of the playground partner network (PLAYGROUND-PARTIES-1), without a database. */
class PartnerNetworkDemoDataTest {

  private static List<ContactSpec> allContacts() {
    List<ContactSpec> all = new ArrayList<>();
    for (CustomerSpec customer : PartnerNetworkDemoData.CUSTOMERS) {
      all.addAll(customer.contacts());
      customer.linked().forEach(linked -> all.addAll(linked.contacts()));
    }
    PartnerNetworkDemoData.SUPPLIERS.forEach(supplier -> all.add(supplier.contact()));
    return all;
  }

  @Test
  void everySeededAddressIsOnAReservedDomainSoNothingReachesAStranger() {
    assertThat(allContacts())
        .isNotEmpty()
        .allSatisfy(contact -> assertThat(contact.email()).endsWith(".example"));
  }

  @Test
  void aPersonIsListedOnceAndCarriesAPhone() {
    Set<String> emails = new HashSet<>();
    for (ContactSpec contact : allContacts()) {
      assertThat(emails.add(contact.email())).as(contact.email()).isTrue();
      assertThat(contact.phone()).isNotBlank().hasSizeLessThanOrEqualTo(30);
      assertThat(contact.name()).isNotBlank().hasSizeLessThanOrEqualTo(120);
    }
  }

  @Test
  void aCompanyHasOneContactPerRoleBecauseEachIsThePrimaryOfItsRole() {
    for (CustomerSpec customer : PartnerNetworkDemoData.CUSTOMERS) {
      assertThat(customer.contacts()).extracting(ContactSpec::role).doesNotHaveDuplicates();
      for (LinkedSpec linked : customer.linked()) {
        assertThat(linked.contacts()).extracting(ContactSpec::role).doesNotHaveDuplicates();
      }
    }
  }

  @Test
  void everyCustomerHasABuyerTwoOrMoreContactsTwoCardsAndLinkedCompanies() {
    for (CustomerSpec customer : PartnerNetworkDemoData.CUSTOMERS) {
      assertThat(customer.contacts()).hasSizeGreaterThanOrEqualTo(2);
      assertThat(customer.contacts())
          .extracting(ContactSpec::role)
          .contains(PartnerContactRole.BUYER);
      assertThat(customer.cards()).hasSize(2);
      assertThat(customer.linked()).hasSizeGreaterThanOrEqualTo(2);
    }
  }

  @Test
  void eachOwnerHasExactlyOnePrimaryCardAndTheFirstOneIsIt() {
    List<List<CardSpec>> owners = new ArrayList<>();
    for (CustomerSpec customer : PartnerNetworkDemoData.CUSTOMERS) {
      owners.add(customer.cards());
      customer.linked().forEach(linked -> owners.add(linked.cards()));
    }
    for (List<CardSpec> cards : owners) {
      assertThat(cards).isNotEmpty();
      assertThat(cards.stream().filter(CardSpec::primary)).hasSize(1);
      assertThat(cards.get(0).primary()).isTrue();
      assertThat(cards).extracting(CardSpec::label).doesNotHaveDuplicates();
    }
  }

  @Test
  void linkedCompaniesAreUniqueByNameAndTaxPrefixAndOneIsSharedByTwoCustomers() {
    List<LinkedSpec> all = new ArrayList<>();
    PartnerNetworkDemoData.CUSTOMERS.forEach(customer -> all.addAll(customer.linked()));

    Set<String> names = new HashSet<>();
    long sharedCount = 0;
    for (LinkedSpec linked : all) {
      if (!names.add(linked.name())) {
        sharedCount++;
        assertThat(linked).isSameAs(PartnerNetworkDemoData.CHANNEL_FREIGHT);
      }
    }
    assertThat(sharedCount).isEqualTo(1);

    List<String> prefixes = all.stream().map(LinkedSpec::taxPrefix).distinct().toList();
    assertThat(prefixes).hasSize(names.size());
  }

  @Test
  void verificationIsMixedOnPurposeSoBothStatesShowOnTheOrderForm() {
    long unverifiedCompanies =
        PartnerNetworkDemoData.CUSTOMERS.stream()
            .flatMap(customer -> customer.linked().stream())
            .filter(linked -> !linked.verified())
            .count();
    long unverifiedCards =
        PartnerNetworkDemoData.CUSTOMERS.stream()
            .flatMap(
                customer ->
                    Stream.concat(
                        customer.cards().stream(),
                        customer.linked().stream().flatMap(linked -> linked.cards().stream())))
            .filter(card -> !card.verified())
            .count();

    assertThat(unverifiedCompanies).isEqualTo(1);
    assertThat(unverifiedCards).isEqualTo(2);
  }

  @Test
  void countriesCarryBothCodesTheServicesWant() {
    for (CustomerSpec customer : PartnerNetworkDemoData.CUSTOMERS) {
      assertThat(customer.place().alpha3()).hasSize(3);
      assertThat(customer.place().alpha2()).hasSize(2);
      customer
          .linked()
          .forEach(
              linked -> {
                assertThat(linked.place().alpha3()).hasSize(3);
                assertThat(linked.place().alpha2()).hasSize(2);
              });
    }
  }
}
