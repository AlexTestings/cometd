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
package org.cometd.annotation;

import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import org.cometd.annotation.client.ClientAnnotationProcessor;
import org.cometd.bayeux.Channel;
import org.cometd.bayeux.ChannelId;
import org.cometd.bayeux.Message;
import org.cometd.bayeux.client.ClientSession;
import org.cometd.bayeux.client.ClientSessionChannel;
import org.cometd.client.BayeuxClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ClientAnnotationProcessorTest extends AbstractClientServerTest {
    private BayeuxClient bayeuxClient;
    private ClientAnnotationProcessor processor;

    @BeforeEach
    public void init() {
        bayeuxClient = newBayeuxClient();
        processor = new ClientAnnotationProcessor(bayeuxClient);
    }

    @AfterEach
    public void destroy() {
        disconnectBayeuxClient(bayeuxClient);
    }

    @Test
    public void testNull() {
        boolean processed = processor.process(null);
        Assertions.assertFalse(processed);
    }

    @Test
    public void testNonServiceAnnotatedClass() {
        NonServiceAnnotatedService s = new NonServiceAnnotatedService();
        boolean processed = processor.process(s);
        Assertions.assertFalse(processed);
        Assertions.assertNull(s.session);
    }

    public static class NonServiceAnnotatedService {
        @Session
        private ClientSession session;
    }

    @Test
    public void testInjectClientSessionOnField() {
        InjectClientSessionOnFieldService s = new InjectClientSessionOnFieldService();
        boolean processed = processor.process(s);
        Assertions.assertTrue(processed);
        Assertions.assertNotNull(s.session);
    }

    @Service
    public static class InjectClientSessionOnFieldService {
        @Session
        private BayeuxClient session;
    }

    @Test
    public void testInjectClientSessionOnMethod() {
        InjectClientSessionOnMethodService s = new InjectClientSessionOnMethodService();
        boolean processed = processor.process(s);
        Assertions.assertTrue(processed);
        Assertions.assertNotNull(s.session);
    }

    @Service
    public static class InjectClientSessionOnMethodService {
        private ClientSession session;

        @Session
        private void set(ClientSession session) {
            this.session = session;
        }
    }

    @Test
    public void testListenUnlisten() throws Exception {
        AtomicReference<Message> handshakeRef = new AtomicReference<>();
        CountDownLatch handshakeLatch = new CountDownLatch(1);
        AtomicReference<Message> connectRef = new AtomicReference<>();
        CountDownLatch connectLatch = new CountDownLatch(1);
        AtomicReference<Message> disconnectRef = new AtomicReference<>();
        CountDownLatch disconnectLatch = new CountDownLatch(1);

        ListenUnlistenService s = new ListenUnlistenService(handshakeRef, handshakeLatch, connectRef, connectLatch, disconnectRef, disconnectLatch);
        boolean processed = processor.process(s);
        Assertions.assertTrue(processed);

        bayeuxClient.handshake();
        Assertions.assertTrue(handshakeLatch.await(5, TimeUnit.SECONDS));
        Message handshake = handshakeRef.get();
        Assertions.assertNotNull(handshake);
        Assertions.assertTrue(handshake.isSuccessful());

        Assertions.assertTrue(connectLatch.await(5, TimeUnit.SECONDS));
        Message connect = connectRef.get();
        Assertions.assertNotNull(connect);
        Assertions.assertTrue(connect.isSuccessful());

        processed = processor.deprocessCallbacks(s);
        Assertions.assertTrue(processed);

        // Listener method must not be notified, since we have deconfigured
        bayeuxClient.disconnect(1000);
    }

    @Service
    public static class ListenUnlistenService {
        private final AtomicReference<Message> handshakeRef;
        private final CountDownLatch handshakeLatch;
        private final AtomicReference<Message> connectRef;
        private final CountDownLatch connectLatch;
        private final AtomicReference<Message> disconnectRef;
        private final CountDownLatch disconnectLatch;

        public ListenUnlistenService(AtomicReference<Message> handshakeRef, CountDownLatch handshakeLatch, AtomicReference<Message> connectRef, CountDownLatch connectLatch, AtomicReference<Message> disconnectRef, CountDownLatch disconnectLatch) {

            this.handshakeRef = handshakeRef;
            this.handshakeLatch = handshakeLatch;
            this.connectRef = connectRef;
            this.connectLatch = connectLatch;
            this.disconnectRef = disconnectRef;
            this.disconnectLatch = disconnectLatch;
        }

        @Listener(Channel.META_HANDSHAKE)
        public void metaHandshake(Message handshake) {
            handshakeRef.set(handshake);
            handshakeLatch.countDown();
        }

        @Listener(Channel.META_CONNECT)
        public void metaConnect(Message connect) {
            connectRef.set(connect);
            connectLatch.countDown();
        }

        @Listener(Channel.META_DISCONNECT)
        public void metaDisconnect(Message connect) {
            disconnectRef.set(connect);
            disconnectLatch.countDown();
        }
    }

    @Test
    public void testSubscribeUnsubscribe() throws Exception {
        AtomicReference<Message> messageRef = new AtomicReference<>();
        AtomicReference<CountDownLatch> messageLatch = new AtomicReference<>(new CountDownLatch(1));

        SubscribeUnsubscribeService s = new SubscribeUnsubscribeService(messageRef, messageLatch);
        boolean processed = processor.process(s);
        Assertions.assertTrue(processed);

        CountDownLatch subscribeLatch = new CountDownLatch(1);
        bayeuxClient.getChannel(Channel.META_SUBSCRIBE).addListener((ClientSessionChannel.MessageListener)(channel, message) -> subscribeLatch.countDown());

        bayeuxClient.handshake();
        Assertions.assertTrue(bayeuxClient.waitFor(5000, BayeuxClient.State.CONNECTED));
        Assertions.assertTrue(subscribeLatch.await(5, TimeUnit.SECONDS));

        bayeuxClient.getChannel("/foo").publish(new HashMap<>());
        Assertions.assertTrue(messageLatch.get().await(5, TimeUnit.SECONDS));

        CountDownLatch unsubscribeLatch = new CountDownLatch(1);
        bayeuxClient.getChannel(Channel.META_UNSUBSCRIBE).addListener((ClientSessionChannel.MessageListener)(channel, message) -> unsubscribeLatch.countDown());

        processor.deprocessCallbacks(s);
        Assertions.assertTrue(unsubscribeLatch.await(5, TimeUnit.SECONDS));

        messageLatch.set(new CountDownLatch(1));

        bayeuxClient.getChannel("/foo").publish(new HashMap<>());
        Assertions.assertFalse(messageLatch.get().await(1, TimeUnit.SECONDS));
    }

    @Service
    public static class SubscribeUnsubscribeService {
        private final AtomicReference<Message> messageRef;
        private final AtomicReference<CountDownLatch> messageLatch;

        public SubscribeUnsubscribeService(AtomicReference<Message> messageRef, AtomicReference<CountDownLatch> messageLatch) {
            this.messageRef = messageRef;
            this.messageLatch = messageLatch;
        }

        @Subscription("/foo")
        public void foo(Message message) {
            messageRef.set(message);
            messageLatch.get().countDown();
        }
    }

    @Test
    public void testUsage() throws Exception {
        CountDownLatch connectLatch = new CountDownLatch(1);
        AtomicReference<CountDownLatch> messageLatch = new AtomicReference<>();

        UsageService s = new UsageService(connectLatch, messageLatch);
        processor.process(s);
        Assertions.assertTrue(s.initialized);
        Assertions.assertFalse(s.connected);

        CountDownLatch subscribeLatch = new CountDownLatch(1);
        bayeuxClient.getChannel(Channel.META_SUBSCRIBE).addListener((ClientSessionChannel.MessageListener)(channel, message) -> subscribeLatch.countDown());

        bayeuxClient.handshake();
        Assertions.assertTrue(connectLatch.await(5, TimeUnit.SECONDS));
        Assertions.assertTrue(s.connected);
        Assertions.assertTrue(subscribeLatch.await(5, TimeUnit.SECONDS));

        messageLatch.set(new CountDownLatch(1));
        bayeuxClient.getChannel("/foo").publish(new HashMap<>());
        Assertions.assertTrue(messageLatch.get().await(5, TimeUnit.SECONDS));

        processor.deprocess(s);
        Assertions.assertFalse(s.initialized);

        messageLatch.set(new CountDownLatch(1));
        bayeuxClient.getChannel("/foo").publish(new HashMap<>());
        Assertions.assertFalse(messageLatch.get().await(1, TimeUnit.SECONDS));
    }

    @Service
    public static class UsageService {
        private final CountDownLatch connectLatch;
        private final AtomicReference<CountDownLatch> messageLatch;
        private boolean initialized;
        private boolean connected;
        @Session
        private ClientSession session;

        public UsageService(CountDownLatch connectLatch, AtomicReference<CountDownLatch> messageLatch) {
            this.connectLatch = connectLatch;
            this.messageLatch = messageLatch;
        }

        @PostConstruct
        private void init() {
            initialized = true;
        }

        @PreDestroy
        private void destroy() {
            initialized = false;
        }

        @Listener(Channel.META_CONNECT)
        public void metaConnect(Message connect) {
            connected = connect.isSuccessful();
            connectLatch.countDown();
        }

        @Subscription("/foo")
        public void foo(Message message) {
            messageLatch.get().countDown();
        }
    }

    @Test
    public void testInjectables() {
        Injectable i = new DerivedInjectable();
        InjectablesService s = new InjectablesService();
        processor = new ClientAnnotationProcessor(bayeuxClient, i);
        boolean processed = processor.process(s);
        Assertions.assertTrue(processed);

        Assertions.assertSame(i, s.i);
    }

    static class Injectable {
    }

    static class DerivedInjectable extends Injectable {
    }

    @Service
    public static class InjectablesService {
        @Inject
        private Injectable i;
    }

    @Test
    public void testResubscribeOnRehandshake() throws Exception {
        AtomicReference<CountDownLatch> messageLatch = new AtomicReference<>();
        ResubscribeOnRehandshakeService s = new ResubscribeOnRehandshakeService(messageLatch);
        boolean processed = processor.process(s);
        Assertions.assertTrue(processed);

        AtomicReference<CountDownLatch> subscribeLatch = new AtomicReference<>(new CountDownLatch(1));
        bayeuxClient.getChannel(Channel.META_SUBSCRIBE).addListener((ClientSessionChannel.MessageListener)(channel, message) -> subscribeLatch.get().countDown());

        bayeuxClient.handshake();
        Assertions.assertTrue(bayeuxClient.waitFor(1000, BayeuxClient.State.CONNECTED));
        Assertions.assertTrue(subscribeLatch.get().await(5, TimeUnit.SECONDS));

        messageLatch.set(new CountDownLatch(1));
        bayeuxClient.getChannel("/foo").publish("data1");
        Assertions.assertTrue(messageLatch.get().await(5, TimeUnit.SECONDS));

        bayeuxClient.disconnect();
        Assertions.assertTrue(bayeuxClient.waitFor(1000, BayeuxClient.State.DISCONNECTED));

        // Wait for the /meta/connect to return.
        Thread.sleep(1000);

        // Rehandshake
        subscribeLatch.set(new CountDownLatch(1));
        bayeuxClient.handshake();
        Assertions.assertTrue(bayeuxClient.waitFor(1000, BayeuxClient.State.CONNECTED));
        Assertions.assertTrue(subscribeLatch.get().await(5, TimeUnit.SECONDS));

        // Republish, it must have resubscribed
        messageLatch.set(new CountDownLatch(1));
        bayeuxClient.getChannel("/foo").publish("data2");
        Assertions.assertTrue(messageLatch.get().await(5, TimeUnit.SECONDS));

        bayeuxClient.disconnect();
        Assertions.assertTrue(bayeuxClient.waitFor(1000, BayeuxClient.State.DISCONNECTED));

        boolean deprocessed = processor.deprocess(s);
        Assertions.assertTrue(deprocessed);

        // Wait for the /meta/connect to return.
        Thread.sleep(1000);

        // Rehandshake
        bayeuxClient.handshake();
        Assertions.assertTrue(bayeuxClient.waitFor(1000, BayeuxClient.State.CONNECTED));

        // Republish, it must not have resubscribed
        messageLatch.set(new CountDownLatch(1));
        bayeuxClient.getChannel("/foo").publish(new HashMap<>());
        Assertions.assertFalse(messageLatch.get().await(1, TimeUnit.SECONDS));
    }

    @Service
    public static class ResubscribeOnRehandshakeService {
        private final AtomicReference<CountDownLatch> messageLatch;

        public ResubscribeOnRehandshakeService(AtomicReference<CountDownLatch> messageLatch) {
            this.messageLatch = messageLatch;
        }

        @Subscription("/foo")
        public void foo(Message message) {
            messageLatch.get().countDown();
        }
    }

    @Test
    public void testListenerWithParameters() throws Exception {
        // Wait for handshake and first connect.
        CountDownLatch latch = new CountDownLatch(2);
        ListenerWithParametersService s = new ListenerWithParametersService(latch);
        boolean processed = processor.process(s);
        Assertions.assertTrue(processed);

        bayeuxClient.handshake();
        Assertions.assertTrue(bayeuxClient.waitFor(1000, BayeuxClient.State.CONNECTED));

        Assertions.assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Service
    public static class ListenerWithParametersService {
        private final CountDownLatch latch;

        public ListenerWithParametersService(CountDownLatch latch) {
            this.latch = latch;
        }

        @Listener("/meta/{action}")
        public void meta(Message message, @Param("action") String action) {
            if ("handshake".equals(action) || "connect".equals(action)) {
                latch.countDown();
            }
        }
    }

    @Test
    public void testSubscriberWithParameters() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        String value1 = "v1";
        String value2 = "v2";
        SubscriberWithParametersService s = new SubscriberWithParametersService(latch, value1, value2);
        boolean processed = processor.process(s);
        Assertions.assertTrue(processed);

        bayeuxClient.handshake();
        Assertions.assertTrue(bayeuxClient.waitFor(1000, BayeuxClient.State.CONNECTED));

        String channel = "/a/" + value1 + "/" + value2 + "/d";
        Assertions.assertFalse(new ChannelId(SubscriberWithParametersService.CHANNEL).bind(new ChannelId(channel)).isEmpty());

        bayeuxClient.getChannel(channel).publish("data");
        Assertions.assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Service
    public static class SubscriberWithParametersService {
        public static final String CHANNEL = "/a/{b}/{c}/d";

        private final CountDownLatch latch;
        private final String value1;
        private final String value2;

        public SubscriberWithParametersService(CountDownLatch latch, String value1, String value2) {
            this.latch = latch;
            this.value1 = value1;
            this.value2 = value2;
        }

        @Subscription(CHANNEL)
        public void service(Message message, @Param("b") String b, @Param("c") String c) {
            Assertions.assertEquals(value1, b);
            Assertions.assertEquals(value2, c);
            latch.countDown();
        }
    }
}
