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
package org.cometd.server.ext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.cometd.bayeux.Channel;
import org.cometd.bayeux.Message;
import org.cometd.bayeux.server.BayeuxServer;
import org.cometd.bayeux.server.ServerMessage;
import org.cometd.bayeux.server.ServerSession;
import org.cometd.server.AbstractBayeuxClientServerTest;
import org.cometd.server.AbstractService;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ExtensionPublishReceivedTest extends AbstractBayeuxClientServerTest {
    private CountingExtension extension = new CountingExtension();

    public ExtensionPublishReceivedTest(String serverTransport) {
        super(serverTransport);
    }

    @Before
    public void prepare() throws Exception {
        startServer(null);
    }

    @Test
    public void testExtension() throws Exception {
        bayeux.addExtension(extension);
        new Publisher(bayeux);

        Request handshake = newBayeuxRequest("[{" +
                "\"channel\": \"/meta/handshake\"," +
                "\"version\": \"1.0\"," +
                "\"minimumVersion\": \"1.0\"," +
                "\"supportedConnectionTypes\": [\"long-polling\"]" +
                "}]");
        ContentResponse response = handshake.send();
        Assert.assertEquals(200, response.getStatus());

        String clientId = extractClientId(response);

        String channel = "/test";
        Request subscribe = newBayeuxRequest("[{" +
                "\"channel\": \"/meta/subscribe\"," +
                "\"clientId\": \"" + clientId + "\"," +
                "\"subscription\": \"" + channel + "\"" +
                "}]");
        response = subscribe.send();
        Assert.assertEquals(200, response.getStatus());

        Assert.assertEquals(0, extension.rcvs.size());
        Assert.assertEquals(4, extension.rcvMetas.size());
        Assert.assertEquals(1, extension.sends.size());
        Assert.assertEquals(4, extension.sendMetas.size());
    }

    private class CountingExtension implements BayeuxServer.Extension {
        private final List<Message> rcvs = new ArrayList<>();
        private final List<Message> rcvMetas = new ArrayList<>();
        private final List<Message> sends = new ArrayList<>();
        private final List<Message> sendMetas = new ArrayList<>();

        @Override
        public boolean rcv(ServerSession from, ServerMessage.Mutable message) {
            rcvs.add(message);
            return true;
        }

        @Override
        public boolean rcvMeta(ServerSession from, ServerMessage.Mutable message) {
            rcvMetas.add(message);
            return true;
        }

        @Override
        public boolean send(ServerSession from, ServerSession to, ServerMessage.Mutable message) {
            sends.add(message);
            return true;
        }

        @Override
        public boolean sendMeta(ServerSession to, ServerMessage.Mutable message) {
            sendMetas.add(message);
            return true;
        }
    }

    public class Publisher extends AbstractService {
        public Publisher(BayeuxServer bayeux) {
            super(bayeux, "test");
            addService(Channel.META_SUBSCRIBE, "emit");
        }

        public void emit(ServerSession remote, ServerMessage message) {
            HashMap<String, Object> data = new HashMap<>();
            data.put("emitted", true);
            remote.deliver(getServerSession(), "/test", data);
        }
    }
}
