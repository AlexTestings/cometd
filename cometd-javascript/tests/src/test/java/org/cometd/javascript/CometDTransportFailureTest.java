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

import java.util.concurrent.atomic.AtomicInteger;

import org.cometd.bayeux.Channel;
import org.cometd.bayeux.server.BayeuxServer;
import org.cometd.bayeux.server.ServerMessage;
import org.cometd.bayeux.server.ServerSession;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CometDTransportFailureTest extends AbstractCometDWebSocketTest {
    @Test
    public void testConnectFailureChangeURL() throws Exception {
        ServerConnector connector2 = new ServerConnector(server);
        connector2.start();
        server.addConnector(connector2);
        
        // Fail the second connect.
        bayeuxServer.addExtension(new ConnectFailureExtension() {
            @Override
            protected boolean onConnect(int count) throws Exception {
                if (count != 2) {
                    return true;
                }
                connector.stop();
                return false;
            }
        });

        String newURL = "http://localhost:" + connector2.getLocalPort() + context.getContextPath() + cometdServletPath;
        evaluateScript("""
                cometd.configure({url: '$U', logLevel: '$L'});
                
                // Replace the transport failure logic.
                const oTF = cometd.onTransportFailure;
                cometd.onTransportFailure = function(message, failureInfo, failureHandler) {
                    if (message.channel === '/meta/connect') {
                        failureInfo.action = 'retry';
                        failureInfo.delay = 0;
                        failureInfo.url = '$N';
                        failureHandler(failureInfo);
                        // Reinstall the original function.
                        cometd.onTransportFailure = oTF;
                    } else {
                        oTF.call(this, message, failureInfo, failureHandler);
                    }
                };
                
                // The second connect fails, the third connect should succeed on the new URL.
                const latch = new Latch(1);
                let url = null;
                let connects = 0;
                cometd.addListener('/meta/connect', message => {
                    ++connects;
                    if (connects === 3 && message.successful) {
                        url = cometd.getTransport().getURL();
                        latch.countDown();
                    }
                });
                
                cometd.handshake();
                """.replace("$U", cometdURL).replace("$L", getLogLevel()).replace("$N", newURL));

        Latch latch = javaScript.get("latch");
        Assertions.assertTrue(latch.await(5000));
        Assertions.assertEquals(newURL, javaScript.get("url"));

        connector2.stop();
    }

    @Test
    public void testConnectFailureChangeTransport() throws Exception {
        bayeuxServer.addExtension(new ConnectFailureExtension() {
            @Override
            protected boolean onConnect(int count) {
                return count != 2;
            }
        });

        evaluateScript("""
                cometd.configure({url: '$U', logLevel: '$L'});
                // Disable the websocket transport.
                cometd.websocketEnabled = false;
                // Add the long-polling transport as fallback.
                cometd.registerTransport('long-polling', originalTransports['long-polling']);
                
                // Replace the transport failure logic.
                const oTF = cometd.onTransportFailure;
                cometd.onTransportFailure = function(message, failureInfo, failureHandler) {
                    if (message.channel === '/meta/connect') {
                        failureInfo.action = 'retry';
                        failureInfo.delay = 0;
                        failureInfo.transport = cometd.findTransport('websocket');
                        failureHandler(failureInfo);
                        /* Reinstall the original function */
                        cometd.onTransportFailure = oTF;
                    } else {
                        oTF.call(this, message, failureInfo, failureHandler);
                    }
                };

                const latch = new Latch(1);
                let transport = null;
                let connects = 0;
                cometd.addListener('/meta/connect', message => {
                    ++connects;
                    if (connects === 3 && message.successful) {
                        transport = cometd.getTransport().getType();
                        latch.countDown();
                    }
                });
                
                cometd.handshake();
                """.replace("$U", cometdURL).replace("$L", getLogLevel()));

        Latch latch = javaScript.get("latch");
        Assertions.assertTrue(latch.await(5000));
        Assertions.assertEquals("websocket", javaScript.get("transport"));
    }

    private abstract static class ConnectFailureExtension implements BayeuxServer.Extension {
        private final AtomicInteger connects = new AtomicInteger();

        @Override
        public boolean rcvMeta(ServerSession from, ServerMessage.Mutable message) {
            if (Channel.META_CONNECT.equals(message.getChannel())) {
                try {
                    return onConnect(connects.incrementAndGet());
                } catch (Exception x) {
                    throw new RuntimeException(x);
                }
            }
            return true;
        }

        protected abstract boolean onConnect(int count) throws Exception;
    }
}
