/*
 * Copyright (c) 2019-2029, Dreamlu 卢春梦 (596392912@qq.com & www.dreamlu.net).
 * <p>
 * Licensed under the GNU LESSER GENERAL PUBLIC LICENSE 3.0;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.gnu.org/licenses/lgpl.html
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.metrics;

import io.micrometer.common.lang.NonNull;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.undertow.Undertow;
import io.undertow.server.handlers.MetricsHandler;
import io.undertow.server.session.SessionManagerStatistics;
import org.springframework.boot.web.embedded.undertow.UndertowServletWebServer;
import org.springframework.boot.web.embedded.undertow.UndertowWebServer;

import java.util.concurrent.TimeUnit;

/**
 * Undertow Session Metrics
 */
public class UndertowSessionMetrics extends UndertowMetrics {

	/**
	 * session
	 */
	private static final String METRIC_NAME_SESSIONS_ACTIVE_MAX 				= UNDERTOW_METRIC_NAME_PREFIX + ".sessions.active.max";
	private static final String METRIC_NAME_SESSIONS_ACTIVE_CURRENT 			= UNDERTOW_METRIC_NAME_PREFIX + ".sessions.active.current";
	private static final String METRIC_NAME_SESSIONS_CREATED 					= UNDERTOW_METRIC_NAME_PREFIX + ".sessions.created";
	private static final String METRIC_NAME_SESSIONS_EXPIRED 					= UNDERTOW_METRIC_NAME_PREFIX + ".sessions.expired";
	private static final String METRIC_NAME_SESSIONS_REJECTED 					= UNDERTOW_METRIC_NAME_PREFIX + ".sessions.rejected";
	private static final String METRIC_NAME_SESSIONS_ALIVE_MAX 					= UNDERTOW_METRIC_NAME_PREFIX + ".sessions.alive.max";

	public UndertowSessionMetrics(MetricsHandler metricsHandler) {
		super(metricsHandler);
	}

	public UndertowSessionMetrics(MetricsHandler metricsHandler, String namePrefix) {
		super(metricsHandler, namePrefix);
	}

	public UndertowSessionMetrics(MetricsHandler metricsHandler, String namePrefix, Iterable<Tag> tags) {
		super(metricsHandler, namePrefix, tags);
	}


	/**
	 * Bind metrics to the given registry.
	 * @param registry
	 * @param undertow
	 * @param tags
	 */
	@Override
	public void bindTo(@NonNull MeterRegistry registry, UndertowWebServer undertowWebServer, Undertow undertow, MetricsHandler metricsHandler, String namePrefix, Iterable<Tag> tags){
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
	 * Register session metrics.
	 * @param registry
	 * @param statistics
	 */
	private void registerSessionStatistics(MeterRegistry registry, SessionManagerStatistics statistics, String namePrefix, Iterable<Tag> tags) {
		Gauge.builder(METRIC_NAME_SESSIONS_ACTIVE_MAX, statistics, SessionManagerStatistics::getMaxActiveSessions)
				.tags(tags)
				.baseUnit(BaseUnits.SESSIONS)
				.register(registry);

		Gauge.builder(METRIC_NAME_SESSIONS_ACTIVE_CURRENT, statistics, SessionManagerStatistics::getActiveSessionCount)
				.tags(tags)
				.baseUnit(BaseUnits.SESSIONS)
				.register(registry);

		FunctionCounter.builder(METRIC_NAME_SESSIONS_CREATED, statistics, SessionManagerStatistics::getCreatedSessionCount)
				.tags(tags)
				.baseUnit(BaseUnits.SESSIONS)
				.register(registry);

		FunctionCounter.builder(METRIC_NAME_SESSIONS_EXPIRED, statistics, SessionManagerStatistics::getExpiredSessionCount)
				.tags(tags)
				.baseUnit(BaseUnits.SESSIONS)
				.register(registry);

		FunctionCounter.builder(METRIC_NAME_SESSIONS_REJECTED, statistics, SessionManagerStatistics::getRejectedSessions)
				.tags(tags)
				.baseUnit(BaseUnits.SESSIONS)
				.register(registry);

		TimeGauge.builder(METRIC_NAME_SESSIONS_ALIVE_MAX, statistics, TimeUnit.SECONDS, SessionManagerStatistics::getHighestSessionCount)
				.tags(tags)
				.register(registry);
	}

}
