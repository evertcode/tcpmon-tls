package com.cafeina.tcpmon.session;

import java.time.Instant;

/**
 * One captured HTTP exchange, reduced to the fields the fleet overview needs.
 *
 * @param routeId    the route that captured the exchange
 * @param startedAt  when the exchange started, never null
 * @param durationMs the round trip duration, or null when it is unknown
 * @param statusCode the response status code as text, or null when there is no response
 * @param method     the request method, or null when the exchange is not HTTP
 * @param path       the request path, or null when the exchange is not HTTP
 */
public record OverviewExchange(
        String routeId,
        Instant startedAt,
        Integer durationMs,
        String statusCode,
        String method,
        String path) {
}
