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

import java.util.Optional;

/**
 * What a renewal came to: the successor token, or why there is none. The caller answers 401 {@code invalid_token} for
 * {@link Failure#INVALID_TOKEN} (this session has ended) and 403 {@code user_not_active} for
 * {@link Failure#USER_NOT_ACTIVE} (this person may not sign in), so the gateway can tell the two apart.
 */
public record RenewalResult(RenewedSessionToken renewed, Failure failure) {

    /** Why a token was not renewed. */
    public enum Failure {
        /** Unknown, revoked, already renewed (reuse), renewed concurrently, or past the chain's maximum. */
        INVALID_TOKEN,
        /** The token was good, but its user is now disabled, locked, deleted or has an expired password. */
        USER_NOT_ACTIVE
    }

    public static RenewalResult renewed(RenewedSessionToken renewed) {
        return new RenewalResult(renewed, null);
    }

    public static RenewalResult failed(Failure failure) {
        return new RenewalResult(null, failure);
    }

    public Optional<RenewedSessionToken> successor() {
        return Optional.ofNullable(renewed);
    }
}
