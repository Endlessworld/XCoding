/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.xr21.ai.agent.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Permission settings manager for persisting ALLOW_ALWAYS and REJECT_ALWAYS permissions.
 *
 * @author Endless
 */
@Slf4j
public class PermissionSettings {

    private static final String SETTINGS_DIR = ".XCoding";
    private static final String SETTINGS_FILE = "settings.local.json";
    private static final ObjectMapper objectMapper = JsonMapper.builder()
            .serializationInclusion(JsonInclude.Include.NON_NULL).build();
    private static final ConcurrentMap<String, PermissionAction> permissionCache = new ConcurrentHashMap<>();

    public static Settings load(String cwd) {
        Path settingsPath = getSettingsPath(cwd);
        if (!Files.exists(settingsPath)) {
            return createDefaultSettings();
        }
        try {
            Settings settings = objectMapper.readValue(Files.readString(settingsPath), Settings.class);
            initializeCache(settings);
            log.info("[PermissionSettings] Loaded permissions from: {}", settingsPath);
            return settings;
        } catch (IOException e) {
            log.error("[PermissionSettings] Failed to load settings", e);
            return createDefaultSettings();
        }
    }

    public static void save(String cwd, Settings settings) {
        Path settingsPath = getSettingsPath(cwd);
        try {
            Path parentDir = settingsPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            Files.writeString(settingsPath, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(settings));
            initializeCache(settings);
        } catch (IOException e) {
            log.error("[PermissionSettings] Failed to save settings", e);
        }
    }

    public static void addAllowPermission(String cwd, String toolPattern) {
        Settings settings = load(cwd);
        if (settings.getPermissions() == null) settings.setPermissions(new Settings.Permissions());
        if (settings.getPermissions().getAllow() == null) settings.getPermissions().setAllow(new ArrayList<>());
        if (!settings.getPermissions().getAllow().contains(toolPattern)) {
            settings.getPermissions().getAllow().add(toolPattern);
            save(cwd, settings);
        }
    }

    public static void addRejectPermission(String cwd, String toolPattern) {
        Settings settings = load(cwd);
        if (settings.getPermissions() == null) settings.setPermissions(new Settings.Permissions());
        if (settings.getPermissions().getReject() == null) settings.getPermissions().setReject(new ArrayList<>());
        if (!settings.getPermissions().getReject().contains(toolPattern)) {
            settings.getPermissions().getReject().add(toolPattern);
            save(cwd, settings);
        }
    }

    public static boolean matchesPattern(String toolName, String arguments, String pattern) {
        if (pattern == null || toolName == null) return false;
        if (!pattern.contains("(")) return toolName.equals(pattern);
        try {
            int idx = pattern.indexOf('(');
            String ptn = pattern.substring(0, idx);
            String pargs = pattern.substring(idx + 1, pattern.length() - 1);
            if (!toolName.equals(ptn)) return false;
            if (pargs.isEmpty()) return true;
            if (pargs.endsWith(":*"))
                return arguments != null && arguments.startsWith(pargs.substring(0, pargs.length() - 2));
            return (arguments == null ? "" : arguments).equals(pargs);
        } catch (Exception e) {
            return false;
        }
    }

    public static PermissionAction checkPermission(String toolName, String arguments) {
        String key = toolName + (arguments == null || arguments.isEmpty() ? "" : "(" + arguments + ")");
        PermissionAction action = permissionCache.get(key);
        if (action != null) return action;
        for (var entry : permissionCache.entrySet()) {
            if (matchesPattern(toolName, arguments, entry.getKey())) return entry.getValue();
        }
        return null;
    }

    private static void initializeCache(Settings settings) {
        permissionCache.clear();
        if (settings == null || settings.getPermissions() == null) return;
        if (settings.getPermissions().getAllow() != null) {
            for (String p : settings.getPermissions().getAllow()) permissionCache.put(p, PermissionAction.ALLOW);
        }
        if (settings.getPermissions().getReject() != null) {
            for (String p : settings.getPermissions().getReject()) permissionCache.put(p, PermissionAction.REJECT);
        }
    }

    private static Path getSettingsPath(String cwd) {
        return Paths.get(cwd, SETTINGS_DIR, SETTINGS_FILE);
    }

    private static Settings createDefaultSettings() {
        Settings s = new Settings();
        Settings.Permissions p = new Settings.Permissions();
        p.setAllow(new ArrayList<>());
        p.setReject(new ArrayList<>());
        s.setPermissions(p);
        return s;
    }

    public static void reload(String cwd) {
        load(cwd);
    }

    public enum PermissionAction {ALLOW, REJECT}

    @Data
    public static class Settings {
        private Permissions permissions;

        @Data
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Permissions {
            private List<String> allow;
            private List<String> reject;
        }
    }
}