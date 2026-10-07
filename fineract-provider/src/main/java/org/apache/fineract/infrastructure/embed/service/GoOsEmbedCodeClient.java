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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Mints a one-time embed code at the fiter-os gateway ({@code POST /api/auth/embed-codes}), server to server.
 *
 * <p>
 * The user's Fineract key is sent to the gateway in the request body and nowhere else: it is never logged, never put in
 * an exception message, and never returned. Only the code comes back to the browser, so the key no longer crosses into
 * the chat panel's iframe (fiter-os gap G-337, finding W-4).
 */
@Component
@ConditionalOnProperty(name = "acme.embed.enabled", havingValue = "true", matchIfMissing = true)
public final class GoOsEmbedCodeClient {

    static final String MINT_PATH = "/api/auth/embed-codes";

    private final URI mintUri;
    private final Duration timeout;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    public GoOsEmbedCodeClient(@Value("${acme.embed.go-os-base-url:}") String baseUrl,
            @Value("${acme.embed.timeout:PT5S}") Duration timeout) {
        this.mintUri = mintUri(baseUrl);
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    static URI mintUri(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        String trimmed = baseUrl.strip().replaceAll("/+$", "");
        URI uri = URI.create(trimmed + MINT_PATH);
        if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) {
            throw new IllegalArgumentException("acme.embed.go-os-base-url must be an http(s) URL");
        }
        return uri;
    }

    public EmbedCode mint(String key) {
        if (mintUri == null) {
            throw new EmbedCodeException(503, "embed codes are not configured (acme.embed.go-os-base-url is empty)");
        }
        HttpResponse<String> response;
        try {
            String body = json.writeValueAsString(Map.of("key", key));
            HttpRequest request = HttpRequest.newBuilder(mintUri).timeout(timeout).header("Content-Type", "application/json")
                    .header("Accept", "application/json").POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmbedCodeException(502, "the fiter-os gateway did not answer", e);
        } catch (IOException e) {
            throw new EmbedCodeException(502, "the fiter-os gateway did not answer", e);
        }

        int status = response.statusCode();
        if (status == 401 || status == 429) {
            throw new EmbedCodeException(status,
                    status == 401 ? "the gateway did not accept the user's key" : "too many embed codes for this user; try again shortly");
        }
        if (status != 200) {
            throw new EmbedCodeException(502, "the fiter-os gateway answered " + status);
        }
        try {
            JsonNode node = json.readTree(response.body());
            String code = node.path("code").asText("");
            if (code.isEmpty()) {
                throw new EmbedCodeException(502, "the fiter-os gateway answered without a code");
            }
            return new EmbedCode(code, node.path("expires_in_seconds").asLong(60));
        } catch (IOException e) {
            throw new EmbedCodeException(502, "the fiter-os gateway answered with unreadable JSON", e);
        }
    }
}
