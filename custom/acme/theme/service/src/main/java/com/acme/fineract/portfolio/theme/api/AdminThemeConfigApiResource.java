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

import com.acme.fineract.portfolio.theme.data.AdminThemeConfigData;
import com.acme.fineract.portfolio.theme.service.ThemeReadPlatformService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.stereotype.Component;

@Path("/v1/themes/config")
@Component
@Tag(name = "Theme - Admin Config", description = "Tenant-wide theme defaults, including the default skin, default mode and global branding.")
@RequiredArgsConstructor
public class AdminThemeConfigApiResource {

    private final PlatformSecurityContext context;
    private final ThemeReadPlatformService readService;
    private final DefaultToApiJsonSerializer<AdminThemeConfigData> readSerializer;
    private final DefaultToApiJsonSerializer<CommandProcessingResult> commandSerializer;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Retrieve the tenant theme configuration")
    public String retrieve() {
        context.authenticatedUser();
        return readSerializer.serialize(readService.retrieveAdminConfig());
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Update tenant theme configuration", description = "Requires the `UPDATE_ADMINTHEMECONFIG` permission.")
    public String update(final String apiRequestBodyAsJson) {
        final CommandWrapper commandRequest = new ThemeCommandWrapperBuilder().updateAdminThemeConfig().withJson(apiRequestBodyAsJson)
                .build();
        return commandSerializer.serialize(commandsSourceWritePlatformService.logCommandSource(commandRequest));
    }
}
