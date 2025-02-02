/*
 * Copyright (c) 2008-2020 the original author or authors.
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.Assert;
import org.junit.Test;
import org.mozilla.javascript.ScriptableObject;

public class CometDMultiPublishTest extends AbstractCometDLongPollingTest {
    @Override
    protected void customizeContext(ServletContextHandler context) throws Exception {
        super.customizeContext(context);
        PublishThrowingFilter filter = new PublishThrowingFilter();
        FilterHolder filterHolder = new FilterHolder(filter);
        context.addFilter(filterHolder, cometdServletPath + "/*", EnumSet.of(DispatcherType.REQUEST));
    }

    @Test
    public void testMultiPublish() throws Throwable {
        defineClass(Latch.class);
        evaluateScript("var readyLatch = new Latch(1);");
        Latch readyLatch = get("readyLatch");
        evaluateScript("cometd.addListener('/meta/connect', readyLatch, 'countDown');");
        evaluateScript("cometd.init({url: '" + cometdURL + "', logLevel: '" + getLogLevel() + "'});");
        Assert.assertTrue(readyLatch.await(5000));

        evaluateScript("var subscribeLatch = new Latch(1);");
        Latch subscribeLatch = get("subscribeLatch");
        evaluateScript("cometd.addListener('/meta/subscribe', subscribeLatch, 'countDown');");
        evaluateScript("var latch = new Latch(1);");
        Latch latch = get("latch");
        evaluateScript("cometd.subscribe('/echo', latch, latch.countDown);");
        Assert.assertTrue(subscribeLatch.await(5000));

        defineClass(Handler.class);
        evaluateScript("var handler = new Handler();");
        Handler handler = get("handler");
        evaluateScript("cometd.addListener('/meta/publish', handler, 'handle');");
        evaluateScript("var disconnect = new Latch(1);");
        Latch disconnect = get("disconnect");
        evaluateScript("cometd.addListener('/meta/disconnect', disconnect, disconnect.countDown);");

        AtomicReference<List<Throwable>> failures = new AtomicReference<List<Throwable>>(new ArrayList<Throwable>());
        handler.expect(failures, 4);
        disconnect.reset(1);

        // These publish are sent without waiting each one to return,
        // so they will be queued. The second publish will fail, we
        // expect the following to fail as well, in order.
        evaluateScript("cometd.publish('/echo', {id: 1});" +
                "cometd.publish('/echo', {id: 2});" +
                "cometd.publish('/echo', {id: 3});" +
                "cometd.publish('/echo', {id: 4});" +
                "cometd.disconnect();");

        Assert.assertTrue(latch.await(5000));
        Assert.assertTrue(failures.get().toString(), handler.await(5000));
        Assert.assertTrue(failures.get().toString(), failures.get().isEmpty());
        Assert.assertTrue(disconnect.await(5000));
    }

    public static class Handler extends ScriptableObject {
        private int id;
        private AtomicReference<List<Throwable>> failures;
        private CountDownLatch latch;

        @Override
        public String getClassName() {
            return "Handler";
        }

        public void jsFunction_handle(Object jsMessage) {
            Map message = (Map)Utils.jsToJava(jsMessage);
            Boolean successful = (Boolean)message.get("successful");
            ++id;
            if (id == 1) {
                // First publish should succeed
                if (successful == null || !successful) {
                    failures.get().add(new AssertionError("Publish " + id + " expected successful"));
                }
            } else if (id == 2 || id == 3 || id == 4) {
                // Second publish should fail because of the server
                // Third and fourth are soft failed by the CometD implementation
                if (successful == null || successful) {
                    failures.get().add(new AssertionError("Publish " + id + " expected unsuccessful"));
                } else {
                    Map data = (Map)((Map)((Map)message.get("failure")).get("message")).get("data");
                    int dataId = ((Number)data.get("id")).intValue();
                    if (dataId != id) {
                        failures.get().add(new AssertionError("data id " + dataId + ", expecting " + id));
                    }
                }
            }
            latch.countDown();
        }

        public void expect(AtomicReference<List<Throwable>> failures, int count) {
            this.failures = failures;
            latch = new CountDownLatch(count);
        }

        public boolean await(long timeout) throws InterruptedException {
            return latch.await(timeout, TimeUnit.MILLISECONDS);
        }
    }

    public static class PublishThrowingFilter implements Filter {
        private int messages;

        @Override
        public void init(FilterConfig filterConfig) throws ServletException {
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
            doFilter((HttpServletRequest)request, (HttpServletResponse)response, chain);
        }

        private void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
            String uri = request.getRequestURI();
            if (!uri.endsWith("/handshake") && !uri.endsWith("/connect")) {
                ++messages;
            }
            // The third non-handshake and non-connect message will be the second publish, throw
            if (messages == 3) {
                throw new IOException();
            }
            chain.doFilter(request, response);
        }

        @Override
        public void destroy() {
        }
    }
}
