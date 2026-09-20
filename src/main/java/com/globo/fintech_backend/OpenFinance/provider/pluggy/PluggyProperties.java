package com.globo.fintech_backend.OpenFinance.provider.pluggy;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "pluggy")
public record PluggyProperties(
        String clientId,
        String clientSecret,
        @DefaultValue("https://api.pluggy.ai") String baseUrl
) {

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }
}
