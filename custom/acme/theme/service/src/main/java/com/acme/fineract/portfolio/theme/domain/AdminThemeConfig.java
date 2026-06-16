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
package com.acme.fineract.portfolio.theme.domain;

import com.acme.fineract.portfolio.theme.serialization.JsonNodeAttributeConverter;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableCustom;

@Entity
@Table(name = "m_acme_admin_theme_config")
@Getter
@Setter
@NoArgsConstructor
public class AdminThemeConfig extends AbstractAuditableCustom {

    @Column(name = "default_skin_id", nullable = false)
    private String defaultSkinId = "fiter-default";

    @Column(name = "default_mode", nullable = false)
    @Convert(converter = ThemeModeAttributeConverter.class)
    private ThemeMode defaultMode = ThemeMode.DAY;

    @Column(name = "allow_user_theme_change", nullable = false)
    private boolean allowUserThemeChange = true;

    @Column(name = "allow_user_mode_change", nullable = false)
    private boolean allowUserModeChange = true;

    @Column(name = "global_branding_json", columnDefinition = "TEXT")
    @Convert(converter = JsonNodeAttributeConverter.class)
    private JsonNode globalBranding;
}
