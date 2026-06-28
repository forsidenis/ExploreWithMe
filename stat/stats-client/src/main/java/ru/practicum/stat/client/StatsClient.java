package ru.practicum.stat.client;

import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class StatsClient {
    private final RestClient restClient;
    private final DiscoveryClient discoveryClient;
    private final RetryTemplate retryTemplate;
    // Имя сервиса статистики, под которым он зарегистрирован в Eureka
    private final String statsServiceId = "stats-server";

    public StatsClient(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
        this.restClient = RestClient.builder().build();
        // Настраиваем RetryTemplate: 3 попытки с паузой 3 секунды между ними
        this.retryTemplate = RetryTemplate.builder()
                .maxAttempts(3)
                .fixedBackoff(3000)
                .build();
    }

    /**
     * Получить URI для заданного пути к сервису статистики.
     * Использует DiscoveryClient для получения адреса и порта активного экземпляра.
     */
    private URI getServiceUri(String path) {
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
            log.error("Не удалось получить URI для сервиса статистики: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Отправить запрос на сохранение информации о посещении (hit).
     */
    public EndpointHitDto hit(EndpointHitDto hit) {
        URI uri = getServiceUri("/hit");
        log.debug("Sending hit to {}", uri);

        RestClient.RequestBodySpec request = restClient.post()
                .uri(uri)
                .body(hit);
        return request.retrieve()
                .body(EndpointHitDto.class);
    }

    /**
     * Получить статистику посещений по заданным параметрам.
     */
    public List<ViewStatsDto> getStats(LocalDateTime start, LocalDateTime end,
                                       List<String> uris, Boolean unique) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/stats")
                .queryParam("start", start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .queryParam("end", end.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .queryParam("unique", unique);
        if (uris != null && !uris.isEmpty()) {
            builder.queryParam("uris", String.join(",", uris));
        }
        URI uri = getServiceUri(builder.build().encode().toUriString());
        log.debug("Getting stats from {}", uri);

        RestClient.RequestHeadersSpec<?> request = restClient.get().uri(uri);
        ViewStatsDto[] response = request.retrieve().body(ViewStatsDto[].class);
        return response != null ? Arrays.asList(response) : Collections.emptyList();
    }
}