package com.fabricmanagement.platform.communication.infra.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.communication.config.GoogleMapsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class GoogleMapsAutocompleteTest {
  private GoogleMapsClient client() {
    GoogleMapsProperties properties = new GoogleMapsProperties();
    properties.setApiKey("test-key");
    return new GoogleMapsClient(properties);
  }

  @Test
  void countryRestrictionAndSessionAreSentAsExplicitParameters() {
    GoogleMapsClient client = client();
    RestTemplate rest = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    server
        .expect(
            content()
                .json("{\"includedRegionCodes\":[\"gb\"],\"sessionToken\":\"search-session\"}"))
        .andRespond(withSuccess("{\"suggestions\":[]}", MediaType.APPLICATION_JSON));
    assertThat(client.autocomplete("14 Mill Lane", "GB", "search-session").getPredictions())
        .isEmpty();
    server.verify();
  }

  @Test
  void unrestrictedSearchUsesBiasInsteadOfCountryRestriction() {
    GoogleMapsClient client = client();
    RestTemplate rest = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    server
        .expect(content().json("{\"regionCode\":\"gb\"}"))
        .andRespond(withSuccess("{\"suggestions\":[]}", MediaType.APPLICATION_JSON));
    client.autocomplete("14 Mill Lane", null);
    server.verify();
  }

  @Test
  void providerFailureIsNotReportedAsAnEmptySuccessfulSearch() {
    GoogleMapsClient client = client();
    RestTemplate rest = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    server.expect(content().json("{\"input\":\"14 Mill Lane\"}")).andRespond(withServerError());
    assertThatThrownBy(() -> client.autocomplete("14 Mill Lane", null))
        .isInstanceOf(PlatformDomainException.class);
    server.verify();
  }
}
