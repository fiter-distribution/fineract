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
package org.apache.fineract.infrastructure.fitersession.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.fitersession.service.IssuedSessionToken;
import org.apache.fineract.infrastructure.fitersession.service.RenewedSessionToken;
import org.apache.fineract.infrastructure.fitersession.service.SessionTokenException;
import org.apache.fineract.infrastructure.fitersession.service.SessionTokenService;
import org.apache.fineract.infrastructure.fitersession.service.SessionTokens;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.Role;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Short-lived session tokens for fiter-os (ADR 0032 option B there). Mint takes a Basic-authenticated user; renew and
 * revoke take the token itself.
 */
@Slf4j
@Path("/v1/fiter/session-tokens")
@Component
@ConditionalOnProperty(name = "fiter.session-token.enabled", havingValue = "true", matchIfMissing = true)
@Tag(name = "Fiter Session Tokens", description = "Short-lived read tokens for fiter-os")
@RequiredArgsConstructor
public class SessionTokenApiResource {

    private final PlatformSecurityContext context;
    private final SessionTokenService service;
    private final ObjectMapper json = new ObjectMapper();

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Mint a session token", description = "Body {maxSeconds}. Returns {token, expiresInSeconds, chainExpiresInSeconds}.")
    public Response mint(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorization, String body) {
        if (SessionTokens.bearerToken(authorization) != null) {
            return error(400, "a session token cannot mint another");
        }
        AppUser user = context.authenticatedUser();
        long maxSeconds;
        try {
            JsonNode node = body == null || body.isBlank() ? null : json.readTree(body);
            JsonNode value = node == null ? null : node.get("maxSeconds");
            if (value == null || !value.canConvertToLong()) {
                return error(400, "maxSeconds is required");
            }
            maxSeconds = value.asLong();
        } catch (JsonProcessingException e) {
            return error(400, "body is not JSON");
        }
        try {
            return ok(tokenFields(service.mint(user, maxSeconds)));
        } catch (SessionTokenException e) {
            return error(e.getStatus(), e.getMessage());
        }
    }

    @POST
    @Path("renew")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Renew a session token", description = "Ends the token presented and returns the next, with the user's details.")
    public Response renew(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorization) {
        String token = SessionTokens.bearerToken(authorization);
        if (token == null) {
            return error(401, "session token not valid");
        }
        try {
            RenewedSessionToken renewed = service.renew(token);
            Map<String, Object> body = tokenFields(renewed.token());
            body.putAll(userFields(renewed.user()));
            return ok(body);
        } catch (SessionTokenException e) {
            return error(e.getStatus(), e.getMessage());
        }
    }

    @DELETE
    @Path("current")
    @Operation(summary = "Revoke a session token", description = "Ends every token of the presented token's chain.")
    public Response revoke(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorization) {
        String token = SessionTokens.bearerToken(authorization);
        if (token != null) {
            service.revoke(token);
        }
        return Response.noContent().header("Cache-Control", "no-store").build();
    }

    static Map<String, Object> tokenFields(IssuedSessionToken token) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", token.token());
        body.put("expiresInSeconds", token.expiresInSeconds());
        body.put("chainExpiresInSeconds", token.chainExpiresInSeconds());
        return body;
    }

    /** The fields /authentication returns, without the Basic key. */
    static Map<String, Object> userFields(AppUser user) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("authenticated", true);
        body.put("userId", user.getId());
        body.put("username", user.getUsername());
        if (user.getOffice() != null) {
            body.put("officeId", user.getOffice().getId());
            body.put("officeName", user.getOffice().getName());
        }
        body.put("staffId", user.getStaffId());
        body.put("staffDisplayName", user.getStaffDisplayName());
        List<Map<String, Object>> roles = new ArrayList<>();
        if (user.getRoles() != null) {
            for (Role role : user.getRoles()) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("id", role.getId());
                r.put("name", role.getName());
                roles.add(r);
            }
        }
        body.put("roles", roles);
        List<String> permissions = new ArrayList<>();
        for (GrantedAuthority authority : user.getAuthorities()) {
            permissions.add(authority.getAuthority());
        }
        body.put("permissions", permissions);
        return body;
    }

    private Response ok(Map<String, Object> body) {
        try {
            return Response.ok(json.writeValueAsString(body), MediaType.APPLICATION_JSON).header("Cache-Control", "no-store").build();
        } catch (JsonProcessingException e) {
            return error(500, "could not write the response");
        }
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
