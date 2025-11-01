package com.fabricmanagement.common.platform.communication.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Google Maps Platform Configuration Properties.
 *
 * <p>Externalizes all Google Maps API configuration from application.yml and environment variables.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * @Autowired
 * private GoogleMapsProperties googleMapsProperties;
 *
 * String apiKey = googleMapsProperties.getApiKey();
 * }</pre>
 */
@Component
@ConfigurationProperties(prefix = "application.google.maps")
@Data
public class GoogleMapsProperties {

    /**
     * Google Maps Platform API key (from environment variable GOOGLE_MAPS_API_KEY)
     */
    private String apiKey;

    /**
     * Enable/disable Google Maps features
     */
    private Boolean enabled = true;

    /**
     * Request timeout in milliseconds
     */
    private Integer timeout = 10000;

    /**
     * Region bias for autocomplete (comma-separated country codes: TR,GB,DE,FR,IT,ES)
     */
    private String regionBias = "TR,GB,DE,FR,IT,ES";

    /**
     * Component restrictions (country codes for Places API)
     */
    private String componentRestrictions;
}

