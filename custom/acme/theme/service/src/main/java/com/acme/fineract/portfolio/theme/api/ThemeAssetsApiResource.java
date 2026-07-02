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

import com.acme.fineract.portfolio.theme.domain.ThemeAsset;
import com.acme.fineract.portfolio.theme.domain.ThemeAssetRepository;
import com.acme.fineract.portfolio.theme.exception.ThemeAssetNotFoundException;
import com.acme.fineract.portfolio.theme.service.ThemeReadPlatformService;
import com.acme.fineract.portfolio.theme.service.ThemeWritePlatformService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.springframework.stereotype.Component;

/**
 * Multipart upload + streaming download for theme binary assets (logos, favicons, etc.). The asset rows are tracked in
 * {@code m_acme_theme_asset}; the bytes themselves are persisted through Fineract's {@code ContentStoreService} so they
 * inherit the operator-configured storage backend (filesystem or S3).
 *
 * Note: the upload path does NOT pass through the Fineract command framework. The framework speaks JSON commands, and
 * shoehorning a multipart binary payload through it would silently lose the file. Delete still goes through the command
 * framework so its audit trail matches every other mutation.
 */
@Path("/v1/themes/assets")
@Component
@Tag(name = "Theme - Assets", description = "Logo and branding asset upload, download and delete.")
@RequiredArgsConstructor
public class ThemeAssetsApiResource {

    private final PlatformSecurityContext context;
    private final ThemeReadPlatformService readService;
    private final ThemeWritePlatformService writeService;
    private final ThemeAssetRepository assetRepo;
    private final DefaultToApiJsonSerializer<CommandProcessingResult> commandSerializer;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Upload a theme asset", description = "Multipart upload of a logo / favicon image. The `assetKey` form field identifies the slot (e.g. `global-logo-light`); uploading the same key replaces the previous file.")
    public String upload(@FormDataParam("assetKey") String assetKey, @FormDataParam("file") InputStream fileStream,
            @FormDataParam("file") FormDataContentDisposition fileDetail, @FormDataParam("file") FormDataBodyPart filePart) {
        var user = context.authenticatedUser();
        user.validateHasPermissionTo("CREATE_" + ThemeApiConstants.RESOURCE_THEME_ASSET);
        String contentType = filePart != null && filePart.getMediaType() != null ? filePart.getMediaType().toString()
                : MediaType.APPLICATION_OCTET_STREAM;
        String filename = fileDetail != null ? fileDetail.getFileName() : null;
        long size = fileDetail != null ? fileDetail.getSize() : -1L;
        final CommandProcessingResult result = writeService.uploadAsset(assetKey, filename, contentType, size, fileStream);
        return commandSerializer.serialize(result);
    }

    @GET
    @Path("{assetKey}")
    @Operation(summary = "Stream a theme asset by key", description = "Public endpoint — no authentication required. Theme branding assets must be reachable by the login page before a user has authenticated. Whitelisted in SecurityConfig for `GET /api/*/themes/assets/*`.")
    public Response download(@PathParam("assetKey") String assetKey) {
        var asset = readService.downloadAsset(assetKey);
        StreamingOutput streaming = output -> {
            try (InputStream in = asset.stream()) {
                in.transferTo(output);
            } catch (IOException e) {
                throw new RuntimeException("Failed to stream theme asset", e);
            }
        };
        Response.ResponseBuilder builder = Response.ok(streaming, asset.contentType());
        if (asset.filename() != null) {
            builder.header("Content-Disposition", "inline; filename=\"" + asset.filename() + "\"");
        }
        builder.header("Cache-Control", "public, max-age=3600");
        return builder.build();
    }

    @DELETE
    @Path("{assetKey}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Delete a theme asset")
    public String delete(@PathParam("assetKey") String assetKey) {
        final Long assetId = resolveAssetId(assetKey);
        final CommandWrapper commandRequest = new ThemeCommandWrapperBuilder().deleteThemeAsset(assetId).build();
        return commandSerializer.serialize(commandsSourceWritePlatformService.logCommandSource(commandRequest));
    }

    private Long resolveAssetId(final String assetKey) {
        return assetRepo.findByAssetKey(assetKey).map(ThemeAsset::getId).orElseThrow(() -> new ThemeAssetNotFoundException(assetKey));
    }
}
