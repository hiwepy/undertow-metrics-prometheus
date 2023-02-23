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

import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.web.embedded.undertow.UndertowBuilderCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * Undertow Metrics 配置
 *
 * @author L.cm
 */

@AutoConfigureAfter(value =  {MetricsAutoConfiguration.class, SimpleMetricsExportAutoConfiguration.class})
@AutoConfigureBefore(value = {ServletWebServerFactoryAutoConfiguration.class})
@ConditionalOnClass(Undertow.class)
// @ImportRuntimeHints(UndertowRuntimeHintsRegistrar.class)
public class UndertowMetricsAutoConfiguration {

	@Bean
	public UndertowMetricsHandlerWrapper undertowMetricsHandlerWrapper() {
		return new UndertowMetricsHandlerWrapper();
	}

	@Bean
	public UndertowMetrics undertowMetrics(UndertowMetricsHandlerWrapper undertowMetricsHandlerWrapper) {
		return new UndertowMetrics(undertowMetricsHandlerWrapper);
	}

	@Bean
	public UndertowBuilderCustomizer undertowBuilderCustomizerEnableStatistics() {
		return builder -> builder.setServerOption(UndertowOptions.ENABLE_STATISTICS, true);
	}

}
