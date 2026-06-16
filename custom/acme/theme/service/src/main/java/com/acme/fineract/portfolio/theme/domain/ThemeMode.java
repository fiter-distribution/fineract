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

/**
 * Accessibility mode that drives which color palette / contrast variant is applied. Persisted as the lowercase string
 * representation (e.g. {@code "day"}) to stay aligned with the FE contract in {@code theme.types.ts}.
 */
public enum ThemeMode {

    DAY, DUSK, NIGHT;

    public String apiValue() {
        return name().toLowerCase();
    }

    public static ThemeMode fromApiValue(String value) {
        if (value == null) return null;
        for (ThemeMode m : values()) {
            if (m.apiValue().equalsIgnoreCase(value) || m.name().equalsIgnoreCase(value)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Unknown theme mode: " + value);
    }
}
