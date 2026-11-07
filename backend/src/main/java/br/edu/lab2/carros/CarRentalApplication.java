package br.edu.lab2.carros;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

@SpringBootApplication
public class CarRentalApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(CarRentalApplication.class);
        ApplicationListener<ApplicationEnvironmentPreparedEvent> datasourceUrlNormalizer =
                CarRentalApplication::normalizeDatasourceUrl;
        application.addListeners(datasourceUrlNormalizer);
        application.run(args);
    }

    private static void normalizeDatasourceUrl(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();
        String configuredUrl = environment.getProperty("spring.datasource.url");

        if (configuredUrl == null || configuredUrl.isBlank() || configuredUrl.startsWith("jdbc:")) {
            return;
        }

        try {
            URI uri = URI.create(configuredUrl);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("postgresql") || scheme.equalsIgnoreCase("postgres"))
                    || uri.getRawAuthority() == null || uri.getRawAuthority().isBlank() || uri.getRawFragment() != null) {
                throw new IllegalArgumentException();
            }

            String authority = uri.getRawAuthority();
            String userInfo = uri.getRawUserInfo();
            if (userInfo != null) {
                int at = authority.lastIndexOf('@');
                authority = authority.substring(at + 1);
            }

            StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://").append(authority);
            if (uri.getRawPath() != null) {
                jdbcUrl.append(uri.getRawPath());
            }
            if (uri.getRawQuery() != null) {
                jdbcUrl.append('?').append(uri.getRawQuery());
            }

            Map<String, Object> normalizedProperties = new HashMap<>();
            normalizedProperties.put("spring.datasource.url", jdbcUrl.toString());

            if (userInfo != null) {
                int separator = userInfo.indexOf(':');
                String username = decodeUriComponent(separator < 0 ? userInfo : userInfo.substring(0, separator));
                String password = separator < 0 ? null : decodeUriComponent(userInfo.substring(separator + 1));

                if (!environment.containsProperty("SPRING_DATASOURCE_USERNAME") && !username.isEmpty()) {
                    normalizedProperties.put("SPRING_DATASOURCE_USERNAME", username);
                }
                if (!environment.containsProperty("SPRING_DATASOURCE_PASSWORD") && password != null) {
                    normalizedProperties.put("SPRING_DATASOURCE_PASSWORD", password);
                }
            }

            environment.getPropertySources().addFirst(
                    new MapPropertySource("normalizedPostgresDatasourceUrl", normalizedProperties));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "SPRING_DATASOURCE_URL deve ser uma URL PostgreSQL JDBC ou uma URI PostgreSQL válida.");
        }
    }

    private static String decodeUriComponent(String value) {
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }
}
