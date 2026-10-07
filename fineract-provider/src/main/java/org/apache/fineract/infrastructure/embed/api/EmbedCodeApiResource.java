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
package org.apache.fineract.infrastructure.embed.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.embed.service.EmbedCode;
import org.apache.fineract.infrastructure.embed.service.EmbedCodeException;
import org.apache.fineract.infrastructure.embed.service.GoOsEmbedCodeClient;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * One-time embed codes for the fiter-os chat panel (fiter-os gap G-337).
 *
 * <p>
 * The webapp used to post the user's Fineract key into the chat panel's iframe. It now calls this endpoint with its
 * usual Basic authentication and posts only the code it gets back. The key is read from the request's own
 * {@code Authorization} header, which Spring Security has already verified, so the endpoint takes no key in its body
 * and can only mint for the signed-in user.
 */
@Slf4j
@Path("/v1/fiter/embed-codes")
@Component
@ConditionalOnProperty(name = "acme.embed.enabled", havingValue = "true", matchIfMissing = true)
@Tag(name = "Fiter Embed Codes", description = "One-time codes for the fiter-os chat panel")
@RequiredArgsConstructor
public class EmbedCodeApiResource {

    private static final String BASIC = "basic ";

    private final PlatformSecurityContext context;
    private final GoOsEmbedCodeClient client;
    private final ObjectMapper json = new ObjectMapper();

    @POST
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Mint a one-time code for the fiter-os chat panel", description = "Returns {code, expiresInSeconds}. The code works once, for about a minute.")
    public Response mint(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorization) {
        String username = context.authenticatedUser().getUsername();

        String key = basicKey(authorization);
        if (key == null) {
            // OAuth bearer tokens are not Fineract keys; the gateway cannot check them.
            return error(400, "embed codes need Basic authentication");
        }
        try {
            EmbedCode code = client.mint(key);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("code", code.code());
            body.put("expiresInSeconds", code.expiresInSeconds());
            return Response.ok(json.writeValueAsString(body), MediaType.APPLICATION_JSON).header("Cache-Control", "no-store").build();
        } catch (EmbedCodeException e) {
            // The message names the status only; it never carries the key.
            log.warn("Embed code not minted for user {}: {}", username, e.getMessage());
            return error(e.getStatus(), e.getMessage());
        } catch (JsonProcessingException e) {
            return error(500, "could not write the response");
        }
    }

    static String basicKey(String authorization) {
        if (authorization == null || authorization.length() <= BASIC.length()
                || !authorization.regionMatches(true, 0, BASIC, 0, BASIC.length())) {
            return null;
        }
        String key = authorization.substring(BASIC.length()).strip();
        return key.isEmpty() ? null : key;
    }

    private Response error(int status, String message) {
        String body;
        try {
            body = json.writeValueAsString(Map.of("developerMessage", message));
        } catch (JsonProcessingException e) {
            body = "{}";
        }
        return Response.status(status).type(MediaType.APPLICATION_JSON).header("Cache-Control", "no-store").entity(body).build();
    }
}
