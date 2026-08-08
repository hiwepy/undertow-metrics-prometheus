/*
 * Copyright (c) 2018-present, easy-4-java (https://github.com/easy-4-java).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.undertow.metrics;

import io.micrometer.common.lang.NonNull;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.undertow.Undertow;
import io.undertow.server.handlers.MetricsHandler;
import io.undertow.server.session.SessionManagerStatistics;
import org.springframework.boot.web.embedded.undertow.UndertowServletWebServer;
import org.springframework.boot.web.embedded.undertow.UndertowWebServer;

import java.util.concurrent.TimeUnit;

/**
 * Micrometer binder that exposes the session-manager statistics reported by
 * a Spring Boot {@link UndertowServletWebServer}.
 *
 * <p>This binder is the only one in the family that consumes the Spring
 * Boot-specific {@link UndertowWebServer} view (rather than the bare
 * {@link Undertow} instance) because session-manager statistics are obtained
 * through the Spring Boot deployment manager. When the supplied server is
 * <em>not</em> an {@link UndertowServletWebServer}, no metrics are
 * registered.</p>
 *
 * <p>The exposed metrics (in the {@code .sessions.*} family) are:</p>
 * <ul>
 *     <li>{@code <prefix>.sessions.active.max} &mdash; historical high-water
 *         mark of active sessions.</li>
 *     <li>{@code <prefix>.sessions.active.current} &mdash; current number
 *         of active sessions.</li>
 *     <li>{@code <prefix>.sessions.created} &mdash; cumulative number of
 *         created sessions.</li>
 *     <li>{@code <prefix>.sessions.expired} &mdash; cumulative number of
 *         expired sessions.</li>
 *     <li>{@code <prefix>.sessions.rejected} &mdash; cumulative number of
 *         rejected session-creation attempts.</li>
 *     <li>{@code <prefix>.sessions.alive.max} &mdash; historical high-water
 *         mark of <em>concurrently alive</em> sessions, expressed in
 *         seconds.</li>
 * </ul>
 *
 * @author [@Loong Wan](https://github.com/loong10k)
 * @since 3.0.0
 * @see UndertowMetrics
 * @see SessionManagerStatistics
 */
public class UndertowSessionMetrics extends UndertowMetrics {

	/** Suffix for the maximum-active-sessions gauge (unit: sessions). */
	private static final String METRIC_NAME_SESSIONS_ACTIVE_MAX 				= ".sessions.active.max";
	/** Suffix for the current-active-sessions gauge (unit: sessions). */
	private static final String METRIC_NAME_SESSIONS_ACTIVE_CURRENT 			= ".sessions.active.current";
	/** Suffix for the cumulative-created-sessions counter (unit: sessions). */
	private static final String METRIC_NAME_SESSIONS_CREATED 					= ".sessions.created";
	/** Suffix for the cumulative-expired-sessions counter (unit: sessions). */
	private static final String METRIC_NAME_SESSIONS_EXPIRED 					= ".sessions.expired";
	/** Suffix for the cumulative-rejected-sessions counter (unit: sessions). */
	private static final String METRIC_NAME_SESSIONS_REJECTED 					= ".sessions.rejected";
	/** Suffix for the maximum-concurrently-alive-sessions time gauge (unit: seconds). */
	private static final String METRIC_NAME_SESSIONS_ALIVE_MAX 					= ".sessions.alive.max";

	/**
	 * Create a new session-metrics binder that uses the default metric-name
	 * prefix.
	 *
	 * @param undertowWebServer the Spring-managed Undertow web server to
	 *                          observe; must not be {@code null}.
	 */
	public UndertowSessionMetrics(UndertowWebServer undertowWebServer) {
		super(undertowWebServer);
	}

	/**
	 * Create a new session-metrics binder with a custom metric-name prefix.
	 *
	 * @param undertowWebServer the Spring-managed Undertow web server to
	 *                          observe; must not be {@code null}.
	 * @param namePrefix        the metric-name prefix to prepend to every
	 *                          emitted metric.
	 */
	public UndertowSessionMetrics(UndertowWebServer undertowWebServer, String namePrefix) {
		super(undertowWebServer, namePrefix);
	}

	/**
	 * Create a new session-metrics binder with a custom metric-name prefix
	 * and an additional tag set.
	 *
	 * @param undertowWebServer the Spring-managed Undertow web server to
	 *                          observe; must not be {@code null}.
	 * @param namePrefix        the metric-name prefix to prepend to every
	 *                          emitted metric.
	 * @param tags              additional tags applied to every emitted
	 *                          metric; may be empty.
	 */
	public UndertowSessionMetrics(UndertowWebServer undertowWebServer, String namePrefix, Iterable<Tag> tags) {
		super(undertowWebServer, namePrefix, tags);
	}

	/**
	 * Register session metrics against the supplied registry. The
	 * implementation silently skips registration if the supplied web server
	 * is not an {@link UndertowServletWebServer}, because non-servlet
	 * variants do not expose session-manager statistics.
	 *
	 * @param registry          the Micrometer registry to register with;
	 *                          must not be {@code null}.
	 * @param undertowWebServer the Spring-managed Undertow web server; must
	 *                          not be {@code null}.
	 * @param namePrefix        the metric-name prefix to prepend to every
	 *                          metric.
	 * @param tags              additional tags applied to every metric; may
	 *                          be empty.
	 */
	@Override
	public void bindTo(@NonNull MeterRegistry registry, UndertowWebServer undertowWebServer, String namePrefix, Iterable<Tag> tags){
		// 如果是 web 监控，添加 session 指标
		if (undertowWebServer instanceof UndertowServletWebServer) {
			SessionManagerStatistics statistics = ((UndertowServletWebServer)undertowWebServer).getDeploymentManager()
					.getDeployment()
					.getSessionManager()
					.getStatistics();
			registerSessionStatistics(registry, statistics, namePrefix, tags);
		}
	};

	/**
	 * Register the session-manager gauges and counters for the supplied
	 * statistics snapshot.
	 *
	 * <p>Six meters are registered: two {@link Gauge}s (max-active and
	 * current-active), three {@link FunctionCounter}s (created, expired,
	 * rejected) and a single {@link TimeGauge} (highest alive time in
	 * seconds).</p>
	 *
	 * @param registry   the Micrometer registry to register with; must not be
	 *                   {@code null}.
	 * @param statistics the session-manager statistics snapshot; must not be
	 *                   {@code null}.
	 * @param namePrefix the metric-name prefix to prepend to every metric.
	 * @param tags       additional tags applied to every metric; may be
	 *                   empty.
	 */
	private void registerSessionStatistics(MeterRegistry registry, SessionManagerStatistics statistics, String namePrefix, Iterable<Tag> tags) {

		Gauge.builder(namePrefix + METRIC_NAME_SESSIONS_ACTIVE_MAX, statistics, SessionManagerStatistics::getMaxActiveSessions)
				.tags(tags)
				.baseUnit(BaseUnits.SESSIONS)
				.register(registry);

		Gauge.builder(namePrefix + METRIC_NAME_SESSIONS_ACTIVE_CURRENT, statistics, SessionManagerStatistics::getActiveSessionCount)
				.tags(tags)
				.baseUnit(BaseUnits.SESSIONS)
				.register(registry);

		FunctionCounter.builder(namePrefix + METRIC_NAME_SESSIONS_CREATED, statistics, SessionManagerStatistics::getCreatedSessionCount)
				.tags(tags)
				.baseUnit(BaseUnits.SESSIONS)
				.register(registry);

		FunctionCounter.builder(namePrefix + METRIC_NAME_SESSIONS_EXPIRED, statistics, SessionManagerStatistics::getExpiredSessionCount)
				.tags(tags)
				.baseUnit(BaseUnits.SESSIONS)
				.register(registry);

		FunctionCounter.builder(namePrefix + METRIC_NAME_SESSIONS_REJECTED, statistics, SessionManagerStatistics::getRejectedSessions)
				.tags(tags)
				.baseUnit(BaseUnits.SESSIONS)
				.register(registry);

		TimeGauge.builder(namePrefix + METRIC_NAME_SESSIONS_ALIVE_MAX, statistics, TimeUnit.SECONDS, SessionManagerStatistics::getHighestSessionCount)
				.tags(tags)
				.register(registry);
	}

}