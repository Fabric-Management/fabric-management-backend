package com.fabricmanagement.finance.collection.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fabricmanagement.finance.collection.app.port.CollectionChequeDrawerPort;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.DrawerKind;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.DrawerType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** PAYMENT-PLAN-7 R15: the cheque form starts on the kind the customer's default implies. */
class CollectionChequeDrawerServiceTest {

  @ParameterizedTest
  @CsvSource({"0,ENDORSED", "1,OWN", "60,OWN", "100,OWN"})
  void theDefaultKindFollowsTheCustomersDefaultOwnShare(int defaultOwn, DrawerKind expected) {
    UUID partner = UUID.randomUUID();
    var port = mock(CollectionChequeDrawerPort.class);
    var drawer =
        new CollectionChequeDrawerPort.Drawer(
            UUID.randomUUID(), "Oliver Hartley", DrawerType.INDIVIDUAL, true);
    when(port.customer(partner))
        .thenReturn(
            new CollectionChequeDrawerPort.Customer(
                "Northbridge Cloth", defaultOwn, List.of(drawer)));

    var result = new CollectionChequeDrawerService(port).drawers(partner);

    assertThat(result.defaultDrawerKind()).isEqualTo(expected);
    assertThat(result.customerName()).isEqualTo("Northbridge Cloth");
    assertThat(result.drawers())
        .singleElement()
        .satisfies(d -> assertThat(d.name()).isEqualTo("Oliver Hartley"));
  }
}
