/*
 * Copyright (c) 2008-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.cometd.javascript;

import java.util.EnumSet;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CometDEmptyResponseTest extends AbstractCometDLongPollingTest {
    @Override
    protected void customizeContext(ServletContextHandler context) throws Exception {
        super.customizeContext(context);
        EmptyResponseFilter filter = new EmptyResponseFilter();
        FilterHolder filterHolder = new FilterHolder(filter);
        context.addFilter(filterHolder, cometdServletPath + "/*", EnumSet.of(DispatcherType.REQUEST));
    }

    @Test
    public void testEmptyResponse() throws Exception {
        evaluateScript("""
                cometd.configure({url: '$U', logLevel: '$L'});
                const handshakeLatch = new Latch(1);
                cometd.addListener('/meta/handshake', () => handshakeLatch.countDown());
                const failureLatch = new Latch(1);
                cometd.addListener('/meta/unsuccessful', () => failureLatch.countDown());
                cometd.handshake();
                """.replace("$U", cometdURL).replace("$L", getLogLevel()));

        Latch handshakeLatch = javaScript.get("handshakeLatch");
        Assertions.assertTrue(handshakeLatch.await(5000));
        Latch failureLatch = javaScript.get("failureLatch");
        Assertions.assertTrue(failureLatch.await(5000));

        disconnect();
    }

    public static class EmptyResponseFilter implements Filter {
        @Override
        public void init(FilterConfig filterConfig) {
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
            doFilter((HttpServletRequest)request, (HttpServletResponse)response, chain);
        }

        private void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) {
            response.setContentType("application/json;charset=UTF-8");
        }

        @Override
        public void destroy() {
        }
    }
}
