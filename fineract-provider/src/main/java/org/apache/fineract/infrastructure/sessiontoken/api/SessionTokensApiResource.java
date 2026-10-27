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
package org.apache.fineract.infrastructure.sessiontoken.api;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.data.AuthenticatedUserData;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.sessiontoken.filter.SessionTokenAuthentication;
import org.apache.fineract.infrastructure.sessiontoken.service.IssuedSessionToken;
import org.apache.fineract.infrastructure.sessiontoken.service.RenewalResult;
import org.apache.fineract.infrastructure.sessiontoken.service.RenewedSessionToken;
import org.apache.fineract.infrastructure.sessiontoken.service.SessionTokenService;
import org.apache.fineract.useradministration.data.RoleData;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Session tokens for Fiter Go OS (Go OS ADR 0032). Mint needs a password sign-in (HTTP Basic); renew and revoke take
 * the token in the body, because a token past its 15-minute window can still be renewed or revoked but no longer
 * authenticates a request.
 */
@Path("/v1/fiter/session-tokens")
@Tag(name = "Fiter session tokens", description = "Short-lived tokens that let Fiter Go OS read on a user's behalf without keeping their password.")
@RequiredArgsConstructor
public class SessionTokensApiResource {

    private static final Gson GSON = new Gson();

    private final PlatformSecurityContext context;
    private final SessionTokenService service;
    private final ToApiJsonSerializer<AuthenticatedUserData> userSerializer;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Mint a session token", description = "Signed in with a password (HTTP Basic). Body: {\"maxLifetimeSeconds\": n}, optional; the server's own ceiling applies.")
    public Response mint(final String body) {
        AppUser user = context.authenticatedUser();
        if (SecurityContextHolder.getContext().getAuthentication() instanceof SessionTokenAuthentication) {
            return error(Response.Status.FORBIDDEN, "password_sign_in_required");
        }
        Optional<JsonObject> json = parse(body);
        if (json.isEmpty()) {
            return error(Response.Status.BAD_REQUEST, "invalid_request");
        }
        Duration requested = null;
        if (json.get().has("maxLifetimeSeconds")) {
            Optional<Long> seconds = wholeNumber(json.get().get("maxLifetimeSeconds"));
            if (seconds.isEmpty()) {
                return error(Response.Status.BAD_REQUEST, "invalid_request");
            }
            requested = Duration.ofSeconds(seconds.get());
        }
        return issued(service.mint(user, requested), user);
    }

    @POST
    @Path("renew")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Renew a session token", description = "Body: {\"token\": \"fgt_...\"}. Returns a new token and ends the one presented.")
    public Response renew(final String body) {
        Optional<String> token = token(body);
        if (token.isEmpty()) {
            return error(Response.Status.BAD_REQUEST, "invalid_request");
        }
        RenewalResult result = service.renew(token.get());
        Optional<RenewedSessionToken> renewed = result.successor();
        if (renewed.isPresent()) {
            return issued(renewed.get().issued(), renewed.get().user());
        }
        // 403 says this person may not sign in (the gateway ends every session of theirs); 401 says only this
        // session has ended.
        if (result.failure() == RenewalResult.Failure.USER_NOT_ACTIVE) {
            return error(Response.Status.FORBIDDEN, "user_not_active");
        }
        return error(Response.Status.UNAUTHORIZED, "invalid_token");
    }

    @POST
    @Path("revoke")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Revoke a session token", description = "Body: {\"token\": \"fgt_...\"}. Ends every token renewed from the same sign-in.")
    public Response revoke(final String body) {
        // Same answer whether or not the token was known: revoking is idempotent and reveals nothing.
        token(body).ifPresent(service::revoke);
        return noStore(Response.noContent());
    }

    private Optional<String> token(String body) {
        return parse(body).filter(
                json -> json.has("token") && json.get("token").isJsonPrimitive() && json.get("token").getAsJsonPrimitive().isString())
                .map(json -> json.get("token").getAsString());
    }

    private static Optional<JsonObject> parse(String body) {
        if (body == null || body.isBlank()) {
            return Optional.of(new JsonObject());
        }
        try {
            var element = JsonParser.parseString(body);
            return element.isJsonObject() ? Optional.of(element.getAsJsonObject()) : Optional.empty();
        } catch (JsonParseException e) {
            return Optional.empty();
        }
    }

    /**
     * A minted or renewed token and the user as {@code /authentication} describes them, without the authentication key,
     * so the caller checks roles and permissions at sign-in and on every renewal (Go OS ADR 0032, decision 8).
     */
    private Response issued(IssuedSessionToken issued, AppUser user) {
        JsonObject json = tokenJson(issued);
        json.add("user", JsonParser.parseString(userSerializer.serialize(userData(user))));
        return noStore(Response.ok(GSON.toJson(json), MediaType.APPLICATION_JSON));
    }

    /** A JSON integer, or empty for anything else ("1.5", "60", true, an object). */
    private static Optional<Long> wholeNumber(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return Optional.empty();
        }
        try {
            return Optional.of(element.getAsBigDecimal().longValueExact());
        } catch (ArithmeticException e) {
            return Optional.empty();
        }
    }

    private static AuthenticatedUserData userData(AppUser user) {
        Collection<String> permissions = new ArrayList<>();
        for (GrantedAuthority authority : user.getAuthorities()) {
            permissions.add(authority.getAuthority());
        }
        Collection<RoleData> roles = new ArrayList<>();
        user.getRoles().forEach(role -> roles.add(role.toData()));
        return new AuthenticatedUserData().setUsername(user.getUsername()).setUserId(user.getId()).setOfficeId(user.getOffice().getId())
                .setOfficeName(user.getOffice().getName()).setStaffId(user.getStaffId()).setStaffDisplayName(user.getStaffDisplayName())
                .setOrganisationalRole(user.organisationalRoleData()).setRoles(roles).setPermissions(permissions).setAuthenticated(true);
    }

    private static JsonObject tokenJson(IssuedSessionToken issued) {
        JsonObject json = new JsonObject();
        json.addProperty("token", issued.token());
        json.addProperty("expiresAtEpochMillis", issued.expiresAt());
        json.addProperty("chainExpiresAtEpochMillis", issued.chainExpiresAt());
        return json;
    }

    private static Response error(Response.Status status, String code) {
        return noStore(Response.status(status).entity(GSON.toJson(Map.of("error", code))).type(MediaType.APPLICATION_JSON));
    }

    /** Every answer carries a token or says something about one: never cached. */
    private static Response noStore(Response.ResponseBuilder builder) {
        return builder.header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }
}
