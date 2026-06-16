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

import java.util.Set;

/**
 * Central registry of resource names, permission codes and JSON parameter names for the theme module. Mirrors the
 * {@code RESOURCE_NAME_FOR_PERMISSIONS} / parameter constant convention used in Fineract's
 * {@code CodesApiResource}, {@code GlobalConfigurationApiResource}, etc.
 */
public final class ThemeApiConstants {

    private ThemeApiConstants() {}

    // Resource (entity) names used by permissions and the command framework
    public static final String RESOURCE_USER_THEME_PREFERENCE = "USERTHEMEPREFERENCE";
    public static final String RESOURCE_THEME_SKIN = "THEMESKIN";
    public static final String RESOURCE_ADMIN_THEME_CONFIG = "ADMINTHEMECONFIG";
    public static final String RESOURCE_THEME_ASSET = "THEMEASSET";

    // JSON parameter names — user theme preference
    public static final String SKIN_ID_PARAM = "skinId";
    public static final String MODE_PARAM = "mode";
    public static final String USE_SYSTEM_MODE_PARAM = "useSystemMode";
    public static final String OVERRIDES_PARAM = "overrides";
    public static final String FONT_SIZE_PARAM = "fontSize";
    public static final String REDUCED_MOTION_PARAM = "reducedMotion";
    public static final String HIGH_CONTRAST_PARAM = "highContrast";

    public static final Set<String> USER_PREFERENCE_PARAMS = Set.of(SKIN_ID_PARAM, MODE_PARAM, USE_SYSTEM_MODE_PARAM, OVERRIDES_PARAM);
    public static final Set<String> USER_PREFERENCE_OVERRIDE_PARAMS = Set.of(FONT_SIZE_PARAM, REDUCED_MOTION_PARAM, HIGH_CONTRAST_PARAM);

    // JSON parameter names — theme skin
    public static final String SKIN_KEY_PARAM = "skinKey";
    public static final String NAME_PARAM = "name";
    public static final String DESCRIPTION_PARAM = "description";
    public static final String COLORS_PARAM = "colors";
    public static final String MODES_PARAM = "modes";
    public static final String TYPOGRAPHY_PARAM = "typography";
    public static final String BRANDING_PARAM = "branding";
    public static final String BORDER_RADIUS_PARAM = "borderRadius";
    public static final String SHADOWS_PARAM = "shadows";

    public static final Set<String> SKIN_PARAMS = Set.of(SKIN_KEY_PARAM, NAME_PARAM, DESCRIPTION_PARAM, COLORS_PARAM, MODES_PARAM,
            TYPOGRAPHY_PARAM, BRANDING_PARAM, BORDER_RADIUS_PARAM, SHADOWS_PARAM);

    // JSON parameter names — admin theme config
    public static final String DEFAULT_SKIN_ID_PARAM = "defaultSkinId";
    public static final String DEFAULT_MODE_PARAM = "defaultMode";
    public static final String ALLOW_USER_THEME_CHANGE_PARAM = "allowUserThemeChange";
    public static final String ALLOW_USER_MODE_CHANGE_PARAM = "allowUserModeChange";
    public static final String GLOBAL_BRANDING_PARAM = "globalBranding";

    public static final Set<String> ADMIN_CONFIG_PARAMS = Set.of(DEFAULT_SKIN_ID_PARAM, DEFAULT_MODE_PARAM, ALLOW_USER_THEME_CHANGE_PARAM,
            ALLOW_USER_MODE_CHANGE_PARAM, GLOBAL_BRANDING_PARAM);

    // JSON parameter names — theme asset
    public static final String ASSET_KEY_PARAM = "assetKey";
}
