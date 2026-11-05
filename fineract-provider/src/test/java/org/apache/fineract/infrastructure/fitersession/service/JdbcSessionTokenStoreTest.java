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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcSessionTokenStoreTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final JdbcSessionTokenStore store = new JdbcSessionTokenStore(jdbc);
    private final OffsetDateTime now = OffsetDateTime.parse("2026-11-05T09:00:00Z");

    @Test
    @SuppressWarnings("unchecked")
    void onlyTheLockingReadSelectsForUpdate() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        when(jdbc.query(sql.capture(), any(RowMapper.class), anyString())).thenReturn(List.of());
        assertTrue(store.findByHash("h").isEmpty());
        assertFalse(sql.getValue().contains("FOR UPDATE"));
        store.findByHashForUpdate("h");
        assertTrue(sql.getValue().endsWith("FOR UPDATE"));
    }

    @Test
    void revokeChainTouchesOnlyLiveRowsOfThatChain() {
        store.revokeChain("chain-1", now);
        verify(jdbc).update(eq("UPDATE m_fiter_session_token SET revoked_at = ? WHERE chain_id = ? AND revoked_at IS NULL"),
                eq(Timestamp.from(now.toInstant())), eq("chain-1"));
    }

    @Test
    void purgeDeletesChainsPastTheirMaximum() {
        store.purgeExpired(now);
        verify(jdbc).update(eq("DELETE FROM m_fiter_session_token WHERE chain_expires_at < ?"), eq(Timestamp.from(now.toInstant())));
    }
}
