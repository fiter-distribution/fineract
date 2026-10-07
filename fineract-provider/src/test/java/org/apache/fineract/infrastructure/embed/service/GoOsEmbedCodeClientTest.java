/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.infrastructure.embed.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GoOsEmbedCodeClientTest {

    private static final String KEY = "bWlmb3M6cGFzc3dvcmQ=";

    private HttpServer server;
    private final AtomicReference<String> received = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String gateway(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, out.length == 0 ? -1 : out.length);
            if (out.length > 0) {
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(out);
                }
            }
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    @Test
    void mintsAndReturnsOnlyTheCode() throws IOException {
        String base = gateway(200, "{\"code\":\"one-time\",\"expires_in_seconds\":60}");

        EmbedCode code = new GoOsEmbedCodeClient(base, Duration.ofSeconds(2)).mint(KEY);

        assertEquals("one-time", code.code());
        assertEquals(60, code.expiresInSeconds());
        assertEquals("POST /api/auth/embed-codes", path.get());
        assertEquals("{\"key\":\"" + KEY + "\"}", received.get());
    }

    @Test
    void passesThroughUnauthorizedAndTooManyRequests() throws IOException {
        for (int status : new int[] { 401, 429 }) {
            String base = gateway(status, "{\"error\":\"x\"}");
            EmbedCodeException e = assertThrows(EmbedCodeException.class,
                    () -> new GoOsEmbedCodeClient(base, Duration.ofSeconds(2)).mint(KEY));
            assertEquals(status, e.getStatus());
            stop();
        }
        server = null;
    }

    @Test
    void reportsAnyOtherAnswerAsBadGatewayWithoutTheKey() throws IOException {
        String base = gateway(500, "{\"echo\":\"" + KEY + "\"}");

        EmbedCodeException e = assertThrows(EmbedCodeException.class, () -> new GoOsEmbedCodeClient(base, Duration.ofSeconds(2)).mint(KEY));

        assertEquals(502, e.getStatus());
        assertFalse(e.getMessage().contains(KEY), "the error message must never carry the key");
    }

    @Test
    void refusesAnAnswerWithoutACode() throws IOException {
        String base = gateway(200, "{\"expires_in_seconds\":60}");

        EmbedCodeException e = assertThrows(EmbedCodeException.class, () -> new GoOsEmbedCodeClient(base, Duration.ofSeconds(2)).mint(KEY));

        assertEquals(502, e.getStatus());
    }

    @Test
    void anUnreachableGatewayIsBadGateway() {
        EmbedCodeException e = assertThrows(EmbedCodeException.class,
                () -> new GoOsEmbedCodeClient("http://127.0.0.1:1", Duration.ofSeconds(1)).mint(KEY));

        assertEquals(502, e.getStatus());
        assertFalse(e.getMessage().contains(KEY));
    }

    @Test
    void anUnconfiguredGatewayIsUnavailable() {
        EmbedCodeException e = assertThrows(EmbedCodeException.class, () -> new GoOsEmbedCodeClient("", Duration.ofSeconds(1)).mint(KEY));

        assertEquals(503, e.getStatus());
    }

    @Test
    void theMintUrlIgnoresTrailingSlashesAndRefusesOtherSchemes() {
        assertEquals("https://go-os.example/api/auth/embed-codes", GoOsEmbedCodeClient.mintUri("https://go-os.example//").toString());
        assertThrows(IllegalArgumentException.class, () -> GoOsEmbedCodeClient.mintUri("file:///etc"));
        assertTrue(GoOsEmbedCodeClient.mintUri(" ") == null);
    }
}
