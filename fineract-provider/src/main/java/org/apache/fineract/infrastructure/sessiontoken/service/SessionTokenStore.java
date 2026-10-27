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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.sessiontoken.domain.StoredSessionToken;
import org.springframework.jdbc.core.JdbcTemplate;

/** JDBC access to {@code m_acme_session_token}. The tenant data source is chosen by Fineract's routing data source. */
@RequiredArgsConstructor
public class SessionTokenStore {

    private static final String COLUMNS = "id, chain_id, appuser_id, username, issued_at, expires_at, chain_expires_at, replaced_at, revoked_at";

    private final JdbcTemplate jdbcTemplate;

    public void insert(String tokenHash, String chainId, long appUserId, String username, long issuedAt, long expiresAt,
            long chainExpiresAt) {
        jdbcTemplate.update(
                "INSERT INTO m_acme_session_token (token_hash, chain_id, appuser_id, username, issued_at, expires_at, chain_expires_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                tokenHash, chainId, appUserId, username, issuedAt, expiresAt, chainExpiresAt);
    }

    public Optional<StoredSessionToken> findByHash(String tokenHash) {
        List<StoredSessionToken> rows = jdbcTemplate.query("SELECT " + COLUMNS + " FROM m_acme_session_token WHERE token_hash = ?",
                SessionTokenStore::map, tokenHash);
        return rows.stream().findFirst();
    }

    /**
     * Marks a token renewed, but only if nothing else has renewed or revoked it first. Returns false when another
     * request got there first, which the caller treats as reuse.
     */
    public boolean markReplaced(long id, long now) {
        return jdbcTemplate.update(
                "UPDATE m_acme_session_token SET replaced_at = ? WHERE id = ? AND replaced_at IS NULL AND revoked_at IS NULL", now,
                id) == 1;
    }

    public int revokeChain(String chainId, long now) {
        return jdbcTemplate.update("UPDATE m_acme_session_token SET revoked_at = ? WHERE chain_id = ? AND revoked_at IS NULL", now,
                chainId);
    }

    /** Removes chains past their absolute maximum; nothing in them can authenticate or renew any more. */
    public int purgeExpired(long now) {
        return jdbcTemplate.update("DELETE FROM m_acme_session_token WHERE chain_expires_at <= ?", now);
    }

    private static StoredSessionToken map(ResultSet rs, int rowNum) throws SQLException {
        long replaced = rs.getLong("replaced_at");
        Long replacedAt = rs.wasNull() ? null : replaced;
        long revoked = rs.getLong("revoked_at");
        Long revokedAt = rs.wasNull() ? null : revoked;
        return new StoredSessionToken(rs.getLong("id"), rs.getString("chain_id"), rs.getLong("appuser_id"), rs.getString("username"),
                rs.getLong("issued_at"), rs.getLong("expires_at"), rs.getLong("chain_expires_at"), replacedAt, revokedAt);
    }
}
