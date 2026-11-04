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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** {@link SessionTokenStore} over the tenant database the request is bound to. */
@Component
@ConditionalOnProperty(name = "fiter.session-token.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class JdbcSessionTokenStore implements SessionTokenStore {

    static final String COLUMNS = "id, token_hash, chain_id, appuser_id, password_fingerprint, issued_at, expires_at, chain_expires_at, "
            + "renewed_at, revoked_at";
    static final String SELECT_BY_HASH = "SELECT " + COLUMNS + " FROM m_fiter_session_token WHERE token_hash = ?";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void insert(SessionTokenRecord r) {
        jdbcTemplate.update(
                "INSERT INTO m_fiter_session_token (token_hash, chain_id, appuser_id, password_fingerprint, issued_at, expires_at, "
                        + "chain_expires_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                r.tokenHash(), r.chainId(), r.appUserId(), r.passwordFingerprint(), ts(r.issuedAt()), ts(r.expiresAt()),
                ts(r.chainExpiresAt()));
    }

    @Override
    public Optional<SessionTokenRecord> findByHash(String tokenHash) {
        return first(jdbcTemplate.query(SELECT_BY_HASH, JdbcSessionTokenStore::map, tokenHash));
    }

    @Override
    public Optional<SessionTokenRecord> findByHashForUpdate(String tokenHash) {
        return first(jdbcTemplate.query(SELECT_BY_HASH + " FOR UPDATE", JdbcSessionTokenStore::map, tokenHash));
    }

    @Override
    public void markRenewed(Long id, OffsetDateTime at) {
        jdbcTemplate.update("UPDATE m_fiter_session_token SET renewed_at = ? WHERE id = ?", ts(at), id);
    }

    @Override
    public void revokeChain(String chainId, OffsetDateTime at) {
        jdbcTemplate.update("UPDATE m_fiter_session_token SET revoked_at = ? WHERE chain_id = ? AND revoked_at IS NULL", ts(at), chainId);
    }

    @Override
    public int purgeExpired(OffsetDateTime now) {
        return jdbcTemplate.update("DELETE FROM m_fiter_session_token WHERE chain_expires_at < ?", ts(now));
    }

    private static Optional<SessionTokenRecord> first(List<SessionTokenRecord> rows) {
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    static Timestamp ts(OffsetDateTime value) {
        return value == null ? null : Timestamp.from(value.toInstant());
    }

    static OffsetDateTime time(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant().atOffset(ZoneOffset.UTC);
    }

    static SessionTokenRecord map(ResultSet rs, int row) throws SQLException {
        return new SessionTokenRecord(rs.getLong("id"), rs.getString("token_hash"), rs.getString("chain_id"), rs.getLong("appuser_id"),
                rs.getString("password_fingerprint"), time(rs, "issued_at"), time(rs, "expires_at"), time(rs, "chain_expires_at"),
                time(rs, "renewed_at"), time(rs, "revoked_at"));
    }
}
