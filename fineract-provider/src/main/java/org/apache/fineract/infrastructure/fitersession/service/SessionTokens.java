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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * The token itself: how one is made, recognised and hashed. A token is {@code fst_} followed by 32 random bytes,
 * base64url without padding. Only its SHA-256 hash is ever stored.
 */
public final class SessionTokens {

    public static final String PREFIX = "fst_";
    private static final String BEARER = "bearer ";
    private static final int RANDOM_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private SessionTokens() {}

    public static String newToken() {
        byte[] bytes = new byte[RANDOM_BYTES];
        RANDOM.nextBytes(bytes);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Hex SHA-256 of the token; what the table keys on. */
    public static String hash(String token) {
        return sha256(token);
    }

    /**
     * Hex SHA-256 of the user's stored password hash. A different value later means the password changed after the
     * chain began; {@code m_appuser.last_time_password_updated} is only a date, so it cannot tell.
     */
    public static String fingerprint(String storedPasswordHash) {
        return sha256(storedPasswordHash == null ? "" : storedPasswordHash);
    }

    /** The session token in an Authorization header, or null when the header carries none. */
    public static String bearerToken(String authorization) {
        if (authorization == null || authorization.length() <= BEARER.length()
                || !authorization.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return null;
        }
        String token = authorization.substring(BEARER.length()).strip();
        return token.startsWith(PREFIX) && token.length() > PREFIX.length() ? token : null;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
