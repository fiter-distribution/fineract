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
package com.acme.fineract.portfolio.theme.api;

import com.acme.fineract.portfolio.theme.data.UserThemePreferenceData;
import com.acme.fineract.portfolio.theme.service.ThemeReadPlatformService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.stereotype.Component;

@Path("/v1/themes/preferences/{userId}")
@Component
@Tag(name = "Theme - User Preferences", description = "Per-user theme preference: chosen skin, accessibility mode and overrides.")
@RequiredArgsConstructor
public class UserThemePreferencesApiResource {

    private final PlatformSecurityContext context;
    private final ThemeReadPlatformService readService;
    private final DefaultToApiJsonSerializer<UserThemePreferenceData> readSerializer;
    private final DefaultToApiJsonSerializer<CommandProcessingResult> commandSerializer;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Retrieve a user's theme preference",
            description = "Returns the user's saved theme preference. Falls back to the admin defaults when the user has never customised.")
    @ApiResponses({ @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = UserThemePreferenceData.class))) })
    public String retrieve(@Parameter(description = "userId") @PathParam("userId") final Long userId) {
        var currentUser = context.authenticatedUser();
        if (!currentUser.getId().equals(userId)) {
            currentUser.validateHasReadPermission(ThemeApiConstants.RESOURCE_USER_THEME_PREFERENCE);
        }
        return readSerializer.serialize(readService.retrieveUserPreference(userId));
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Update a user's theme preference",
            description = "Upserts the user's theme preference. Self-access is always allowed; updating another user's preference requires `UPDATE_USERTHEMEPREFERENCE`.")
    public String update(@Parameter(description = "userId") @PathParam("userId") final Long userId, final String apiRequestBodyAsJson) {
        var currentUser = context.authenticatedUser();
        if (!currentUser.getId().equals(userId)) {
            currentUser.validateHasUpdatePermission(ThemeApiConstants.RESOURCE_USER_THEME_PREFERENCE);
        }
        final CommandWrapper commandRequest = new ThemeCommandWrapperBuilder().updateUserThemePreference(userId)
                .withJson(apiRequestBodyAsJson).build();
        final CommandProcessingResult result = commandsSourceWritePlatformService.logCommandSource(commandRequest);
        return commandSerializer.serialize(result);
    }
}
