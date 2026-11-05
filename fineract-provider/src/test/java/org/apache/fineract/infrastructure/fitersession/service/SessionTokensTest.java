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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SessionTokensTest {

    @Test
    void newTokensArePrefixedRandomAndUnpadded() {
        String a = SessionTokens.newToken();
        String b = SessionTokens.newToken();
        assertTrue(a.startsWith("fst_"));
        assertEquals(4 + 43, a.length());
        assertNotEquals(a, b);
    }

    @Test
    void hashIsStableLowercaseHex() {
        String h = SessionTokens.hash("fst_x");
        assertEquals(64, h.length());
        assertTrue(h.matches("[0-9a-f]{64}"));
        assertEquals(h, SessionTokens.hash("fst_x"));
        assertNotEquals(h, SessionTokens.hash("fst_y"));
    }

    @Test
    void fingerprintTellsPasswordHashesApart() {
        assertNotEquals(SessionTokens.fingerprint("$2a$one"), SessionTokens.fingerprint("$2a$two"));
        assertEquals(64, SessionTokens.fingerprint(null).length());
    }

    @Test
    void bearerTokenRecognisesOnlySessionTokens() {
        assertEquals("fst_abc", SessionTokens.bearerToken("Bearer fst_abc"));
        assertEquals("fst_abc", SessionTokens.bearerToken("bearer  fst_abc "));
        assertNull(SessionTokens.bearerToken("Bearer eyJhbGciOi"));
        assertNull(SessionTokens.bearerToken("Bearer fst_"));
        assertNull(SessionTokens.bearerToken("Basic bWlmb3M6cGFzc3dvcmQ="));
        assertNull(SessionTokens.bearerToken(null));
    }
}
