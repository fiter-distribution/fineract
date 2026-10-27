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
package org.apache.fineract.infrastructure.sessiontoken.service;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.sessiontoken.domain.RenewalPolicy;
import org.apache.fineract.infrastructure.sessiontoken.domain.StoredSessionToken;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mints, renews, revokes and checks Go OS session tokens (Go OS ADR 0032, option B). The caller never holds the user's
 * password after sign-in: it holds a token that authenticates for {@code ttl}, is renewed with itself, and stops for
 * good at the chain's absolute maximum.
 */
@Slf4j
@RequiredArgsConstructor
public class SessionTokenService {

    private final SessionTokenStore store;
    private final SessionTokenUsers users;
    private final SessionTokenSettings settings;
    private final Clock clock;

    /** Starts a chain for a user who has just signed in with their password. */
    @Transactional
    public IssuedSessionToken mint(AppUser user, Duration requestedMaxLifetime) {
        long now = clock.millis();
        store.purgeExpired(now);
        Duration max = settings.maxLifetime();
        if (requestedMaxLifetime != null && !requestedMaxLifetime.isNegative() && !requestedMaxLifetime.isZero()
                && requestedMaxLifetime.compareTo(max) < 0) {
            max = requestedMaxLifetime;
        }
        return issue(UUID.randomUUID().toString(), user.getId(), user.getUsername(), now, now + max.toMillis());
    }

    /**
     * Exchanges a token for its successor, or says why it may not be renewed. The user comes back with it so the caller
     * can re-check roles and permissions on every renewal without the password.
     */
    @Transactional
    public RenewalResult renew(String token) {
        Optional<StoredSessionToken> found = find(token);
        if (found.isEmpty()) {
            return RenewalResult.failed(RenewalResult.Failure.INVALID_TOKEN);
        }
        StoredSessionToken stored = found.get();
        long now = clock.millis();
        switch (RenewalPolicy.decide(stored, now)) {
            case REJECT:
                return RenewalResult.failed(RenewalResult.Failure.INVALID_TOKEN);
            case REVOKE_CHAIN:
                log.warn("Session token presented again after renewal; revoking its chain (user id {})", stored.appUserId());
                store.revokeChain(stored.chainId(), now);
                return RenewalResult.failed(RenewalResult.Failure.INVALID_TOKEN);
            case RENEW:
            default:
            break;
        }
        Optional<AppUser> user = users.activeUser(stored);
        if (user.isEmpty()) {
            store.revokeChain(stored.chainId(), now);
            return RenewalResult.failed(RenewalResult.Failure.USER_NOT_ACTIVE);
        }
        if (!store.markReplaced(stored.id(), now)) {
            // Another request renewed or revoked this token between our read and this write: the same token was
            // presented twice. Treat it as reuse.
            log.warn("Session token renewed concurrently; revoking its chain (user id {})", stored.appUserId());
            store.revokeChain(stored.chainId(), now);
            return RenewalResult.failed(RenewalResult.Failure.INVALID_TOKEN);
        }
        IssuedSessionToken issued = issue(stored.chainId(), stored.appUserId(), stored.username(), now, stored.chainExpiresAt());
        return RenewalResult.renewed(new RenewedSessionToken(issued, user.get()));
    }

    /** Ends the chain the token belongs to (sign-out). Returns false for a token this server does not know. */
    @Transactional
    public boolean revoke(String token) {
        Optional<StoredSessionToken> found = find(token);
        found.ifPresent(stored -> store.revokeChain(stored.chainId(), clock.millis()));
        return found.isPresent();
    }

    /** The stored token if it may authenticate a request right now. */
    @Transactional(readOnly = true)
    public Optional<StoredSessionToken> authenticate(String token) {
        long now = clock.millis();
        long grace = settings.readGrace().toMillis();
        return find(token).filter(stored -> stored.authenticatesAt(now, grace));
    }

    private Optional<StoredSessionToken> find(String token) {
        if (!SessionTokenCodec.looksLikeToken(token)) {
            return Optional.empty();
        }
        return store.findByHash(SessionTokenCodec.hash(token));
    }

    private IssuedSessionToken issue(String chainId, long appUserId, String username, long now, long chainExpiresAt) {
        String token = SessionTokenCodec.newToken();
        long expiresAt = Math.min(now + settings.ttl().toMillis(), chainExpiresAt);
        store.insert(SessionTokenCodec.hash(token), chainId, appUserId, username, now, expiresAt, chainExpiresAt);
        return new IssuedSessionToken(token, expiresAt, chainExpiresAt);
    }
}
