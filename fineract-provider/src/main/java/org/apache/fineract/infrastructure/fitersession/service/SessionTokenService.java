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
package org.apache.fineract.infrastructure.fitersession.service;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The rules for Fiter session tokens (ADR 0032 option B in fiter-go-os): a token lasts {@code lifetime}, is renewed
 * with itself and never the password, and each renewal ends the token presented. Renewing a token that was already
 * renewed is taken as theft and ends the whole chain. A renewed token keeps authenticating reads for {@code readGrace},
 * so a read in flight during a renewal does not fail. No chain outlives the maximum fixed when it was minted.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "fiter.session-token.enabled", havingValue = "true", matchIfMissing = true)
public class SessionTokenService {

    private final SessionTokenStore store;
    private final AppUserRepository users;
    private final SessionTokenProperties properties;
    private final Clock clock;

    @Autowired
    public SessionTokenService(SessionTokenStore store, AppUserRepository users, SessionTokenProperties properties) {
        this(store, users, properties, Clock.systemUTC());
    }

    SessionTokenService(SessionTokenStore store, AppUserRepository users, SessionTokenProperties properties, Clock clock) {
        this.store = store;
        this.users = users;
        this.properties = properties;
        this.clock = clock;
    }

    /** Starts a chain for a user who has just authenticated with their password. */
    @Transactional
    public IssuedSessionToken mint(AppUser user, long maxSeconds) {
        if (user == null || user.getId() == null) {
            throw new SessionTokenException(401, "not authenticated");
        }
        if (maxSeconds <= 0) {
            throw new SessionTokenException(400, "maxSeconds must be positive");
        }
        OffsetDateTime now = now();
        Duration chain = Duration.ofSeconds(maxSeconds);
        if (chain.compareTo(properties.maxChain()) > 0) {
            chain = properties.maxChain();
        }
        OffsetDateTime chainExpiresAt = now.plus(chain);
        String token = SessionTokens.newToken();
        SessionTokenRecord record = new SessionTokenRecord(null, SessionTokens.hash(token), UUID.randomUUID().toString(), user.getId(),
                SessionTokens.fingerprint(user.getPassword()), now, earliest(now.plus(properties.lifetime()), chainExpiresAt),
                chainExpiresAt, null, null);
        store.insert(record);
        store.purgeExpired(now);
        return issued(token, record, now);
    }

    /** Ends the token presented and returns the next one in its chain, with the user re-checked. */
    @Transactional(noRollbackFor = SessionTokenException.class)
    public RenewedSessionToken renew(String token) {
        OffsetDateTime now = now();
        SessionTokenRecord record = store.findByHashForUpdate(SessionTokens.hash(token))
                .orElseThrow(() -> new SessionTokenException(401, "session token not valid"));
        if (record.revokedAt() != null || !now.isBefore(record.chainExpiresAt())) {
            throw new SessionTokenException(401, "session token not valid");
        }
        if (record.renewedAt() != null) {
            store.revokeChain(record.chainId(), now);
            log.warn("Session token renewed twice; chain {} revoked", record.chainId());
            throw new SessionTokenException(401, "session token not valid");
        }
        AppUser user = users.findById(record.appUserId()).orElse(null);
        if (!usable(user, record)) {
            store.revokeChain(record.chainId(), now);
            throw new SessionTokenException(401, "session token not valid");
        }
        store.markRenewed(record.id(), now);
        String next = SessionTokens.newToken();
        SessionTokenRecord successor = record.successor(SessionTokens.hash(next), now,
                earliest(now.plus(properties.lifetime()), record.chainExpiresAt()));
        store.insert(successor);
        return new RenewedSessionToken(issued(next, successor, now), user);
    }

    /** Ends every token of the presented token's chain. An unknown token is ignored. */
    @Transactional
    public void revoke(String token) {
        store.findByHash(SessionTokens.hash(token)).ifPresent(record -> store.revokeChain(record.chainId(), now()));
    }

    /** The user a token authenticates a read as, or empty when it does not. */
    @Transactional(readOnly = true)
    public Optional<AppUser> authenticateForRead(String token) {
        OffsetDateTime now = now();
        Optional<SessionTokenRecord> found = store.findByHash(SessionTokens.hash(token));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        SessionTokenRecord record = found.get();
        if (record.revokedAt() != null || !now.isBefore(record.chainExpiresAt())) {
            return Optional.empty();
        }
        boolean live = record.renewedAt() == null ? now.isBefore(record.expiresAt())
                : now.isBefore(record.renewedAt().plus(properties.readGrace()));
        if (!live) {
            return Optional.empty();
        }
        AppUser user = users.findById(record.appUserId()).orElse(null);
        return usable(user, record) ? Optional.of(user) : Optional.empty();
    }

    static boolean usable(AppUser user, SessionTokenRecord record) {
        return user != null && !user.isDeleted() && user.isEnabled() && user.isAccountNonLocked() && user.isAccountNonExpired()
                && user.isCredentialsNonExpired() && SessionTokens.fingerprint(user.getPassword()).equals(record.passwordFingerprint());
    }

    private IssuedSessionToken issued(String token, SessionTokenRecord record, OffsetDateTime now) {
        return new IssuedSessionToken(token, seconds(now, record.expiresAt()), seconds(now, record.chainExpiresAt()));
    }

    private static long seconds(OffsetDateTime from, OffsetDateTime to) {
        return Math.max(0, Duration.between(from, to).getSeconds());
    }

    private static OffsetDateTime earliest(OffsetDateTime a, OffsetDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }
}
