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

import org.cometd.bayeux.server.BayeuxServer;
import org.cometd.bayeux.server.ServerMessage;
import org.cometd.bayeux.server.ServerSession;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CometDWebSocketPublishFailureTest extends AbstractCometDWebSocketTest {
    @Test
    public void testPublishFailure() throws Exception {
        bayeuxServer.addExtension(new DeletePublishExtension());

        evaluateScript("""
                const readyLatch = new Latch(1);
                cometd.addListener('/meta/connect', () => readyLatch.countDown());
                cometd.init({url: '$U', logLevel: '$L'});
                """.replace("$U", cometdURL).replace("$L", getLogLevel()));
        Latch readyLatch = javaScript.get("readyLatch");
        Assertions.assertTrue(readyLatch.await(5000));

        // Wait a while for the /meta/connect to establish.
        Thread.sleep(1000);

        evaluateScript("""
                const subscribeLatch = new Latch(1);
                cometd.addListener('/meta/subscribe', () => subscribeLatch.countDown());
                const subscription = cometd.subscribe('/echo', () => subscribeLatch.countDown());
                """);
        Latch subscribeLatch = javaScript.get("subscribeLatch");
        Assertions.assertTrue(subscribeLatch.await(5000));

        evaluateScript("""
                const publishLatch = new Latch(1);
                cometd.addListener('/meta/publish', () => publishLatch.countDown());
                const failureLatch = new Latch(1);
                cometd.addListener('/meta/unsuccessful', () => failureLatch.countDown());
                cometd.publish('/echo', 'test');
                """);
        Latch publishLatch = javaScript.get("publishLatch");
        Assertions.assertTrue(publishLatch.await(5000));
        Latch failureLatch = javaScript.get("failureLatch");
        Assertions.assertTrue(failureLatch.await(5000));

        disconnect();
    }

    private static class DeletePublishExtension implements BayeuxServer.Extension {
        @Override
        public boolean rcv(ServerSession from, ServerMessage.Mutable message) {
            // The publish will arrive here, just delete it
            return false;
        }
    }
}
