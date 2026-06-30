package ru.practicum.stat.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import ru.practicum.stat.dto.EndpointHitDto;
import ru.practicum.stat.dto.ViewStatsDto;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
@ConditionalOnProperty(name = "stats.client.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class StatsClient {
    private final RestClient restClient;
    private final DiscoveryClient discoveryClient;
    private final RetryTemplate retryTemplate;
    private final String statsServiceId = "STATS-SERVER";
    private final String statsServerUrl;

    public StatsClient(DiscoveryClient discoveryClient,
                       @Value("${stats.server.url:}") String statsServerUrl) {
        this.discoveryClient = discoveryClient;
        this.statsServerUrl = statsServerUrl;
        this.restClient = RestClient.builder().build();
        this.retryTemplate = RetryTemplate.builder()
                .maxAttempts(3)
                .fixedBackoff(3000)
                .build();
    }

    protected StatsClient() {
        this.discoveryClient = null;
        this.statsServerUrl = null;
        this.restClient = RestClient.builder().build();
        this.retryTemplate = RetryTemplate.builder()
                .maxAttempts(3)
                .fixedBackoff(3000)
                .build();
    }

    private URI getServiceUri(String path) {
        if (discoveryClient != null) {
            try {
                ServiceInstance instance = retryTemplate.execute(context -> {
                    List<ServiceInstance> instances = discoveryClient.getInstances(statsServiceId);
                    if (instances.isEmpty()) {
                        throw new IllegalStateException("No instances of service " + statsServiceId + " found in Discovery");
                    }
                    return instances.get(0);
                });
                String baseUrl = "http://" + instance.getHost() + ":" + instance.getPort();
                return URI.create(baseUrl + path);
            } catch (Exception e) {
                log.error("Не удалось получить URI через DiscoveryClient: {}", e.getMessage());
                if (statsServerUrl != null && !statsServerUrl.isEmpty()) {
                    log.warn("Использование fallback URL: {}", statsServerUrl);
                    return URI.create(statsServerUrl + path);
                }
                throw e;
            }
        } else {
            if (statsServerUrl != null && !statsServerUrl.isEmpty()) {
                return URI.create(statsServerUrl + path);
            }
            throw new IllegalStateException("StatsClient не может получить URI: нет DiscoveryClient и не задан stats.server.url");
        }
    }

    public EndpointHitDto hit(EndpointHitDto hit) {
        try {
            URI uri = getServiceUri("/hit");
            log.debug("Sending hit to {}", uri);
            return restClient.post()
                    .uri(uri)
                    .body(hit)
                    .retrieve()
                    .body(EndpointHitDto.class);
        } catch (Exception e) {
            log.error("Ошибка при отправке hit: {}", e.getMessage(), e);
            throw e;
        }
    }

    public List<ViewStatsDto> getStats(LocalDateTime start, LocalDateTime end,
                                       List<String> uris, Boolean unique) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/stats")
                    .queryParam("start", start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                    .queryParam("end", end.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                    .queryParam("unique", unique);
            if (uris != null && !uris.isEmpty()) {
                builder.queryParam("uris", String.join(",", uris));
            }
            URI uri = getServiceUri(builder.build().encode().toUriString());
            log.debug("Getting stats from {}", uri);

            ViewStatsDto[] response = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(ViewStatsDto[].class);
            return response != null ? Arrays.asList(response) : Collections.emptyList();
        } catch (Exception e) {
            log.error("Ошибка при получении статистики: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
}