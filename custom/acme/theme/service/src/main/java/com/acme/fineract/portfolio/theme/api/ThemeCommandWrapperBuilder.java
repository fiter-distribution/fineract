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

import org.apache.fineract.commands.domain.CommandWrapper;

/**
 * Module-local builder that produces {@link CommandWrapper} instances for theme actions without modifying
 * {@code org.apache.fineract.commands.service.CommandWrapperBuilder} in {@code fineract-core}. Following the same
 * fluent shape as the core builder so call sites read identically (e.g.
 * {@code new ThemeCommandWrapperBuilder().updateUserThemePreference(userId).withJson(json).build()}).
 *
 * The resulting {@link CommandWrapper#getTaskPermissionName()} is composed as {@code "ACTION_ENTITY"}, matching the
 * permission code convention used everywhere else in Fineract (e.g. {@code UPDATE_USERTHEMEPREFERENCE}).
 */
public class ThemeCommandWrapperBuilder {

    private String actionName;
    private String entityName;
    private Long entityId;
    private String href;
    private String json = "{}";

    public ThemeCommandWrapperBuilder withJson(final String json) {
        this.json = json;
        return this;
    }

    public ThemeCommandWrapperBuilder updateUserThemePreference(final Long userId) {
        this.actionName = "UPDATE";
        this.entityName = ThemeApiConstants.RESOURCE_USER_THEME_PREFERENCE;
        this.entityId = userId;
        this.href = "/themes/preferences/" + userId;
        return this;
    }

    public ThemeCommandWrapperBuilder createThemeSkin() {
        this.actionName = "CREATE";
        this.entityName = ThemeApiConstants.RESOURCE_THEME_SKIN;
        this.entityId = null;
        this.href = "/themes/skins";
        return this;
    }

    public ThemeCommandWrapperBuilder updateThemeSkin(final Long skinId) {
        this.actionName = "UPDATE";
        this.entityName = ThemeApiConstants.RESOURCE_THEME_SKIN;
        this.entityId = skinId;
        this.href = "/themes/skins/" + skinId;
        return this;
    }

    public ThemeCommandWrapperBuilder deleteThemeSkin(final Long skinId) {
        this.actionName = "DELETE";
        this.entityName = ThemeApiConstants.RESOURCE_THEME_SKIN;
        this.entityId = skinId;
        this.href = "/themes/skins/" + skinId;
        return this;
    }

    public ThemeCommandWrapperBuilder updateAdminThemeConfig() {
        this.actionName = "UPDATE";
        this.entityName = ThemeApiConstants.RESOURCE_ADMIN_THEME_CONFIG;
        this.entityId = null;
        this.href = "/themes/config";
        return this;
    }

    public ThemeCommandWrapperBuilder createThemeAsset() {
        this.actionName = "CREATE";
        this.entityName = ThemeApiConstants.RESOURCE_THEME_ASSET;
        this.entityId = null;
        this.href = "/themes/assets";
        return this;
    }

    public ThemeCommandWrapperBuilder deleteThemeAsset(final Long assetId) {
        this.actionName = "DELETE";
        this.entityName = ThemeApiConstants.RESOURCE_THEME_ASSET;
        this.entityId = assetId;
        this.href = "/themes/assets/" + assetId;
        return this;
    }

    public CommandWrapper build() {
        return new CommandWrapper(null, null, null, null, null, this.actionName, this.entityName, this.entityId, null, this.href, this.json,
                null, null, null, null, null, null, null, null, null);
    }
}
