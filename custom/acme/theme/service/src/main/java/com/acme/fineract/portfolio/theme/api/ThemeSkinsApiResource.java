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

import com.acme.fineract.portfolio.theme.data.ThemeSkinData;
import com.acme.fineract.portfolio.theme.domain.ThemeSkin;
import com.acme.fineract.portfolio.theme.domain.ThemeSkinRepository;
import com.acme.fineract.portfolio.theme.exception.ThemeSkinNotFoundException;
import com.acme.fineract.portfolio.theme.service.ThemeReadPlatformService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.stereotype.Component;

@Path("/v1/themes/skins")
@Component
@Tag(name = "Theme - Skins", description = "Admin-managed custom theme skins (colour palettes, typography, branding bundles).")
@RequiredArgsConstructor
public class ThemeSkinsApiResource {

    private final PlatformSecurityContext context;
    private final ThemeReadPlatformService readService;
    private final ThemeSkinRepository skinRepo;
    private final DefaultToApiJsonSerializer<ThemeSkinData> readSerializer;
    private final DefaultToApiJsonSerializer<CommandProcessingResult> commandSerializer;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List all theme skins")
    public String retrieveAll() {
        context.authenticatedUser();
        final List<ThemeSkinData> skins = readService.retrieveAllSkins();
        return readSerializer.serialize(skins);
    }

    @GET
    @Path("{skinKey}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Retrieve a theme skin by skinKey")
    @ApiResponses({ @ApiResponse(responseCode = "404", description = "Skin not found") })
    public String retrieveOne(@Parameter(description = "skinKey") @PathParam("skinKey") final String skinKey) {
        context.authenticatedUser();
        return readSerializer.serialize(readService.retrieveSkin(skinKey));
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create a new theme skin",
            description = "Requires the `CREATE_THEMESKIN` permission. The body must include a unique `skinKey`.")
    public String create(final String apiRequestBodyAsJson) {
        final CommandWrapper commandRequest = new ThemeCommandWrapperBuilder().createThemeSkin().withJson(apiRequestBodyAsJson).build();
        return commandSerializer.serialize(commandsSourceWritePlatformService.logCommandSource(commandRequest));
    }

    @PUT
    @Path("{skinKey}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Update a theme skin")
    public String update(@Parameter(description = "skinKey") @PathParam("skinKey") final String skinKey,
            final String apiRequestBodyAsJson) {
        final Long skinId = resolveSkinId(skinKey);
        final CommandWrapper commandRequest = new ThemeCommandWrapperBuilder().updateThemeSkin(skinId).withJson(apiRequestBodyAsJson)
                .build();
        return commandSerializer.serialize(commandsSourceWritePlatformService.logCommandSource(commandRequest));
    }

    @DELETE
    @Path("{skinKey}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Delete a theme skin",
            description = "Rejects deletion of system-managed skins.")
    public String delete(@Parameter(description = "skinKey") @PathParam("skinKey") final String skinKey) {
        final Long skinId = resolveSkinId(skinKey);
        final CommandWrapper commandRequest = new ThemeCommandWrapperBuilder().deleteThemeSkin(skinId).build();
        return commandSerializer.serialize(commandsSourceWritePlatformService.logCommandSource(commandRequest));
    }

    private Long resolveSkinId(final String skinKey) {
        return skinRepo.findBySkinKey(skinKey).map(ThemeSkin::getId).orElseThrow(() -> new ThemeSkinNotFoundException(skinKey));
    }
}
