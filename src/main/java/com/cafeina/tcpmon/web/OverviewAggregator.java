package com.cafeina.tcpmon.web;

import com.cafeina.tcpmon.RouteConfig;
import com.cafeina.tcpmon.session.OverviewExchange;
import com.cafeina.tcpmon.session.SessionStore;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the fleet overview: one health summary for every route in a time window.
 *
 * <p>An error is a 5xx response. That matches the definition the route statistics already use.
 * A 4xx response counts as a client error and is reported apart, because it does not indicate
 * that the target is unhealthy.</p>
 */
public final class OverviewAggregator {

    /** The time windows the endpoint accepts, in minutes. */
    public static final Set<Integer> ALLOWED_WINDOW_MINUTES = Set.of(15, 60, 1440);

    /** The maximum number of exchanges that one report reads. */
    static final int MAX_ROWS = 20_000;

    /** The number of buckets in a route sparkline. */
    static final int SPARKLINE_BUCKETS = 12;

    /** A route is degraded at or above this error rate. */
    static final double DEGRADED_ERROR_RATE = 0.05;

    /** A route is failing at or above this error rate. */
    static final double FAILING_ERROR_RATE = 0.20;

    /** The number of slow paths the report lists. */
    static final int SLOWEST_PATH_LIMIT = 5;

    private final SessionStore sessionStore;

    public OverviewAggregator(SessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    /**
     * Tells whether the endpoint accepts the given window.
     *
     * @param windowMinutes the requested window
     * @return true when the window is allowed
     */
    public static boolean isValidWindow(int windowMinutes) {
        return ALLOWED_WINDOW_MINUTES.contains(windowMinutes);
    }

    /**
     * Builds the report for the given window.
     *
     * @param windowMinutes the window, which must be allowed
     * @return the report
     * @throws IllegalArgumentException if the window is not allowed
     */
    public OverviewReport aggregate(int windowMinutes) {
        if (!isValidWindow(windowMinutes)) {
            throw new IllegalArgumentException("Unsupported window: " + windowMinutes);
        }
        Instant now = Instant.now();
        Instant since = now.minus(Duration.ofMinutes(windowMinutes));
        List<OverviewExchange> rows = sessionStore.overviewExchanges(since, MAX_ROWS);
        List<RouteConfig> routes = sessionStore.loadRoutes();
        return summarize(rows, routes, windowMinutes, now);
    }

    /**
     * Reduces the raw exchanges to a report. This method is pure, so it is easy to test.
     *
     * @param rows          the exchanges inside the window
     * @param routes        every configured route
     * @param windowMinutes the window
     * @param now           the end of the window
     * @return the report
     */
    static OverviewReport summarize(
            List<OverviewExchange> rows,
            List<RouteConfig> routes,
            int windowMinutes,
            Instant now) {

        Map<String, List<OverviewExchange>> byRoute = new LinkedHashMap<>();
        Set<String> routeIds = new LinkedHashSet<>();
        for (RouteConfig route : routes) {
            routeIds.add(route.id());
        }
        for (OverviewExchange row : rows) {
            String routeId = row.routeId() == null ? "default" : row.routeId();
            routeIds.add(routeId);
            byRoute.computeIfAbsent(routeId, key -> new ArrayList<>()).add(row);
        }

        Map<String, RouteConfig> routeById = new LinkedHashMap<>();
        for (RouteConfig route : routes) {
            routeById.put(route.id(), route);
        }

        Instant since = now.minus(Duration.ofMinutes(windowMinutes));
        List<RouteOverview> routeOverviews = new ArrayList<>();
        for (String routeId : routeIds) {
            List<OverviewExchange> routeRows = byRoute.getOrDefault(routeId, List.of());
            RouteConfig config = routeById.get(routeId);
            routeOverviews.add(buildRouteOverview(routeId, config, routeRows, since, now));
        }
        routeOverviews.sort(Comparator
                .comparingLong(RouteOverview::errors).reversed()
                .thenComparing(Comparator.comparingLong(RouteOverview::requests).reversed())
                .thenComparing(RouteOverview::routeId));

        return new OverviewReport(
                now.toString(),
                windowMinutes,
                buildTotals(rows),
                routeOverviews,
                buildSlowestPaths(rows));
    }

    private static RouteOverview buildRouteOverview(
            String routeId,
            RouteConfig config,
            List<OverviewExchange> rows,
            Instant since,
            Instant now) {

        long requests = rows.size();
        long errors = countByStatusPrefix(rows, '5');
        long clientErrors = countByStatusPrefix(rows, '4');
        double errorRate = rate(errors, requests);
        List<Integer> durations = sortedDurations(rows);

        return new RouteOverview(
                routeId,
                config == null ? null : config.listener().host() + ":" + config.listener().port(),
                config == null ? null : config.target().host() + ":" + config.target().port(),
                statusFor(requests, errorRate),
                requests,
                errors,
                clientErrors,
                errorRate,
                percentile(durations, 0.50),
                percentile(durations, 0.95),
                buildSparkline(rows, since, now));
    }

    private static OverviewTotals buildTotals(List<OverviewExchange> rows) {
        long requests = rows.size();
        long errors = countByStatusPrefix(rows, '5');
        long clientErrors = countByStatusPrefix(rows, '4');
        List<Integer> durations = sortedDurations(rows);
        return new OverviewTotals(
                requests,
                errors,
                clientErrors,
                rate(errors, requests),
                percentile(durations, 0.50),
                percentile(durations, 0.95));
    }

    private static List<SlowPath> buildSlowestPaths(List<OverviewExchange> rows) {
        Map<String, List<OverviewExchange>> grouped = new LinkedHashMap<>();
        for (OverviewExchange row : rows) {
            if (row.method() == null || row.path() == null || row.durationMs() == null) {
                continue;
            }
            String key = row.routeId() + " " + row.method() + " " + row.path();
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }
        List<SlowPath> paths = new ArrayList<>();
        for (List<OverviewExchange> group : grouped.values()) {
            OverviewExchange first = group.get(0);
            paths.add(new SlowPath(
                    first.method(),
                    first.path(),
                    first.routeId(),
                    percentile(sortedDurations(group), 0.95),
                    group.size()));
        }
        paths.sort(Comparator
                .comparingInt((SlowPath path) -> path.p95Ms() == null ? 0 : path.p95Ms()).reversed()
                .thenComparing(SlowPath::path));
        return paths.size() > SLOWEST_PATH_LIMIT ? List.copyOf(paths.subList(0, SLOWEST_PATH_LIMIT)) : List.copyOf(paths);
    }

    /**
     * Counts the requests in each sparkline bucket, oldest bucket first.
     *
     * @param rows  the exchanges of one route
     * @param since the start of the window
     * @param now   the end of the window
     * @return one count per bucket
     */
    static List<Long> buildSparkline(List<OverviewExchange> rows, Instant since, Instant now) {
        long[] buckets = new long[SPARKLINE_BUCKETS];
        long spanMillis = Math.max(1L, now.toEpochMilli() - since.toEpochMilli());
        for (OverviewExchange row : rows) {
            long offset = row.startedAt().toEpochMilli() - since.toEpochMilli();
            int index = (int) ((offset * SPARKLINE_BUCKETS) / spanMillis);
            buckets[Math.min(SPARKLINE_BUCKETS - 1, Math.max(0, index))] += 1;
        }
        List<Long> result = new ArrayList<>(SPARKLINE_BUCKETS);
        for (long bucket : buckets) {
            result.add(bucket);
        }
        return result;
    }

    /**
     * Returns the nearest rank percentile of the durations.
     *
     * @param sortedDurations the durations, sorted ascending
     * @param percentile      the percentile between 0 and 1
     * @return the value, or null when there is no duration
     */
    static Integer percentile(List<Integer> sortedDurations, double percentile) {
        if (sortedDurations.isEmpty()) {
            return null;
        }
        int rank = (int) Math.ceil(percentile * sortedDurations.size());
        int index = Math.min(sortedDurations.size() - 1, Math.max(0, rank - 1));
        return sortedDurations.get(index);
    }

    private static List<Integer> sortedDurations(List<OverviewExchange> rows) {
        List<Integer> durations = new ArrayList<>();
        for (OverviewExchange row : rows) {
            if (row.durationMs() != null) {
                durations.add(row.durationMs());
            }
        }
        durations.sort(Comparator.naturalOrder());
        return durations;
    }

    private static long countByStatusPrefix(List<OverviewExchange> rows, char prefix) {
        long count = 0;
        for (OverviewExchange row : rows) {
            String status = row.statusCode();
            if (status != null && !status.isEmpty() && status.charAt(0) == prefix) {
                count += 1;
            }
        }
        return count;
    }

    private static double rate(long part, long total) {
        if (total <= 0) {
            return 0.0;
        }
        return Math.round((double) part / total * 10_000.0) / 10_000.0;
    }

    private static String statusFor(long requests, double errorRate) {
        if (requests == 0) {
            return "idle";
        }
        if (errorRate >= FAILING_ERROR_RATE) {
            return "failing";
        }
        if (errorRate >= DEGRADED_ERROR_RATE) {
            return "degraded";
        }
        return "healthy";
    }

    /** The whole report. */
    public record OverviewReport(
            String generatedAt,
            int windowMinutes,
            OverviewTotals totals,
            List<RouteOverview> routes,
            List<SlowPath> slowestPaths) {
    }

    /** The totals across every route. */
    public record OverviewTotals(
            long requests,
            long errors,
            long clientErrors,
            double errorRate,
            Integer p50Ms,
            Integer p95Ms) {
    }

    /** The health of one route. */
    public record RouteOverview(
            String routeId,
            String listener,
            String target,
            String status,
            long requests,
            long errors,
            long clientErrors,
            double errorRate,
            Integer p50Ms,
            Integer p95Ms,
            List<Long> sparkline) {
    }

    /** One slow request path. */
    public record SlowPath(
            String method,
            String path,
            String routeId,
            Integer p95Ms,
            long count) {
    }
}
