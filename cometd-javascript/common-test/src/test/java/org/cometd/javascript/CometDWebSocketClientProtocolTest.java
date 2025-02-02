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

import org.junit.Assert;
import org.junit.Test;

public class CometDWebSocketClientProtocolTest extends AbstractCometDWebSocketTest {
    private static final String PROTOCOL = "bayeux/1.0";

    @Test
    public void testClientWithoutWebSocketProtocolServerWithoutWebSocketProtocol() throws Exception {
        evaluateScript("cometd.configure({" +
                "url: '" + cometdURL + "', " +
                "logLevel: '" + getLogLevel() + "'" +
                "});");

        defineClass(Latch.class);

        evaluateScript("var latch = new Latch(1);");
        Latch latch = get("latch");
        String channelName = "/bar";
        evaluateScript("cometd.addListener('/meta/handshake', function(message)" +
                "{" +
                "   if (message.successful)" +
                "   {" +
                "       cometd.batch(function()" +
                "       {" +
                "           cometd.subscribe('" + channelName + "', latch, 'countDown');" +
                "           cometd.publish('" + channelName + "', {});" +
                "       });" +
                "   }" +
                "});");

        evaluateScript("cometd.handshake();");
        Assert.assertTrue(latch.await(5000));

        evaluateScript("var disconnectLatch = new Latch(1);");
        Latch disconnectLatch = get("disconnectLatch");
        evaluateScript("cometd.addListener('/meta/disconnect', disconnectLatch, disconnectLatch.countDown);");
        evaluateScript("cometd.disconnect();");
        Assert.assertTrue(disconnectLatch.await(5000));
    }

    @Test
    public void testClientWithWebSocketProtocolServerWithoutWebSocketProtocol() throws Exception {
        evaluateScript("cometd.configure({" +
                "url: '" + cometdURL + "', " +
                "protocol: '" + PROTOCOL + "', " +
                "logLevel: '" + getLogLevel() + "'" +
                "});");

        defineClass(Latch.class);

        evaluateScript("var latch = new Latch(1);");
        Latch latch = get("latch");
        String channelName = "/bar";
        evaluateScript("cometd.addListener('/meta/handshake', function(message)" +
                "{" +
                "   if (message.successful)" +
                "   {" +
                "       cometd.batch(function()" +
                "       {" +
                "           cometd.subscribe('" + channelName + "', latch, 'countDown');" +
                "           cometd.publish('" + channelName + "', {});" +
                "       });" +
                "   }" +
                "});");

        evaluateScript("cometd.handshake();");
        // The server tries to match the client protocol but if it can't tries
        // as if the client sent no protocol, which in this case will match
        Assert.assertTrue(latch.await(5000));

        evaluateScript("var disconnectLatch = new Latch(1);");
        Latch disconnectLatch = get("disconnectLatch");
        evaluateScript("cometd.addListener('/meta/disconnect', disconnectLatch, disconnectLatch.countDown);");
        evaluateScript("cometd.disconnect();");
        Assert.assertTrue(disconnectLatch.await(5000));
    }
}
