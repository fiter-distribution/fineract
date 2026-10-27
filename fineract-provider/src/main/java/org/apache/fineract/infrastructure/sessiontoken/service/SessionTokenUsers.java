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

import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.sessiontoken.domain.StoredSessionToken;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Resolves the user a token was issued to, applying the same account checks a Basic sign-in gets (enabled, not deleted,
 * not locked, password not expired). A user disabled after sign-in loses the token on the next request.
 */
@RequiredArgsConstructor
public class SessionTokenUsers {

    private final UserDetailsService userDetailsService;
    private final UserDetailsChecker userDetailsChecker;

    public Optional<AppUser> activeUser(StoredSessionToken token) {
        try {
            UserDetails details = userDetailsService.loadUserByUsername(token.username());
            if (!(details instanceof AppUser user) || !Objects.equals(user.getId(), token.appUserId())) {
                return Optional.empty();
            }
            if (!user.isEnabled() || !user.isAccountNonLocked() || !user.isAccountNonExpired()) {
                return Optional.empty();
            }
            userDetailsChecker.check(user);
            return Optional.of(user);
        } catch (UsernameNotFoundException | AccountStatusException e) {
            return Optional.empty();
        }
    }
}
