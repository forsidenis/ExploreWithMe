package ru.practicum.stat.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.practicum.stat.dto.EndpointHitDto;
import ru.practicum.stat.dto.ViewStatsDto;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Component
@ConditionalOnProperty(name = "stats.client.enabled", havingValue = "false")
@Slf4j
public class NoOpStatsClient extends StatsClient {

    @Override
    public EndpointHitDto hit(EndpointHitDto hit) {
        log.debug("No-op hit: {}", hit);
        return hit;
    }

    @Override
    public List<ViewStatsDto> getStats(LocalDateTime start, LocalDateTime end,
                                       List<String> uris, Boolean unique) {
        log.debug("No-op getStats");
        return Collections.emptyList();
    }
}