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

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** A test store with the JDBC store's semantics. */
final class InMemorySessionTokenStore implements SessionTokenStore {

    final List<SessionTokenRecord> rows = new ArrayList<>();
    private long nextId = 1;

    @Override
    public void insert(SessionTokenRecord r) {
        rows.add(new SessionTokenRecord(nextId++, r.tokenHash(), r.chainId(), r.appUserId(), r.passwordFingerprint(), r.issuedAt(),
                r.expiresAt(), r.chainExpiresAt(), r.renewedAt(), r.revokedAt()));
    }

    @Override
    public Optional<SessionTokenRecord> findByHash(String tokenHash) {
        return rows.stream().filter(r -> r.tokenHash().equals(tokenHash)).findFirst();
    }

    @Override
    public Optional<SessionTokenRecord> findByHashForUpdate(String tokenHash) {
        return findByHash(tokenHash);
    }

    @Override
    public void markRenewed(Long id, OffsetDateTime at) {
        replaceWhere(r -> r.id().equals(id), r -> new SessionTokenRecord(r.id(), r.tokenHash(), r.chainId(), r.appUserId(),
                r.passwordFingerprint(), r.issuedAt(), r.expiresAt(), r.chainExpiresAt(), at, r.revokedAt()));
    }

    @Override
    public void revokeChain(String chainId, OffsetDateTime at) {
        replaceWhere(r -> r.chainId().equals(chainId) && r.revokedAt() == null, r -> new SessionTokenRecord(r.id(), r.tokenHash(),
                r.chainId(), r.appUserId(), r.passwordFingerprint(), r.issuedAt(), r.expiresAt(), r.chainExpiresAt(), r.renewedAt(), at));
    }

    @Override
    public int purgeExpired(OffsetDateTime now) {
        int before = rows.size();
        rows.removeIf(r -> r.chainExpiresAt().isBefore(now));
        return before - rows.size();
    }

    private void replaceWhere(java.util.function.Predicate<SessionTokenRecord> match,
            java.util.function.UnaryOperator<SessionTokenRecord> change) {
        rows.replaceAll(r -> match.test(r) ? change.apply(r) : r);
    }
}
