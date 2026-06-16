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

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableCustom;

@Entity
@Table(name = "m_acme_user_theme_preference")
@Getter
@Setter
@NoArgsConstructor
public class UserThemePreference extends AbstractAuditableCustom {

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "skin_id")
    private String skinId;

    @Column(name = "mode", nullable = false)
    @Convert(converter = ThemeModeAttributeConverter.class)
    private ThemeMode mode = ThemeMode.DAY;

    @Column(name = "use_system_mode", nullable = false)
    private boolean useSystemMode;

    @Column(name = "font_size")
    @Convert(converter = ThemeFontSizeAttributeConverter.class)
    private ThemeFontSize fontSize = ThemeFontSize.NORMAL;

    @Column(name = "reduced_motion")
    private boolean reducedMotion;

    @Column(name = "high_contrast")
    private boolean highContrast;

    public UserThemePreference(Long userId) {
        this.userId = userId;
    }
}
