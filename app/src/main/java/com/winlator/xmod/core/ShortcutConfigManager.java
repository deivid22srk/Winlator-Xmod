package com.winlator.xmod.core;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import com.winlator.xmod.container.Container;
import com.winlator.xmod.container.Shortcut;
import com.winlator.xmod.contentdialog.DXVKConfigDialog;
import com.winlator.xmod.contentdialog.VKD3DConfigDialog;
import com.winlator.xmod.contentdialog.GraphicsDriverConfigDialog;
import com.winlator.xmod.contents.AdrenotoolsManager;
import com.winlator.xmod.contents.ContentProfile;
import com.winlator.xmod.contents.ContentsManager;
import com.winlator.xmod.box64.Box64Preset;
import com.winlator.xmod.box64.Box64PresetManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ShortcutConfigManager {
    private static final String TAG = "ShortcutConfigManager";
    private static final String EXPORT_DIR = "Winlator/ShortcutConfigs";
    private static final String FILE_EXTENSION = ".wsc"; // Winlator Shortcut Config

    /**
     * Exports shortcut settings to a JSON file
     * @param shortcut The shortcut to export
     * @param context Android context
     * @return File object of the exported file, or null if failed
     */
    public static File exportShortcutSettings(Shortcut shortcut, Context context) {
        try {
            File exportDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), EXPORT_DIR);
            if (!exportDir.exists() && !exportDir.mkdirs()) {
                Log.e(TAG, "Failed to create export directory");
                return null;
            }

            File exportFile = new File(exportDir, shortcut.name + FILE_EXTENSION);

            JSONObject configJson = new JSONObject();
            configJson.put("shortcutName", shortcut.name);
            configJson.put("wmClass", shortcut.wmClass);
            configJson.put("exportVersion", "1.0");
            configJson.put("exportDate", System.currentTimeMillis());

            JSONObject extraData = new JSONObject();
            String[] settingKeys = {
                "execArgs", "screenSize", "graphicsDriver", "graphicsDriverConfig",
                "dxwrapper", "ddrawrapper", "dxwrapperConfig", "audioDriver", "emulator",
                "midiSoundFont", "secondaryExec", "execDelay", "fullscreenStretched",
                "inputType", "disableXinput", "relativeMouseMovement", "simTouchScreen",
                "wincomponents", "envVars", "box64Preset", "box64Version", "fexcoreVersion",
                "startupSelection", "sharpnessEffect", "sharpnessLevel", "sharpnessDenoise",
                "controlsProfile", "cpuList"
            };

            for (String key : settingKeys) {
                String value = shortcut.getExtra(key);
                if (value.isEmpty()) {
                    switch (key) {
                        case "graphicsDriverConfig":
                            value = shortcut.container.getGraphicsDriverConfig();
                            break;
                        case "graphicsDriver":
                            value = shortcut.container.getGraphicsDriver();
                            break;
                        case "dxwrapper":
                            value = shortcut.container.getDXWrapper();
                            break;
                        case "ddrawrapper":
                            value = shortcut.container.getDDrawWrapper();
                            break;
                        case "dxwrapperConfig":
                            value = shortcut.container.getDXWrapperConfig();
                            break;
                        case "audioDriver":
                            value = shortcut.container.getAudioDriver();
                            break;
                        case "emulator":
                            value = shortcut.container.getEmulator();
                            break;
                        case "midiSoundFont":
                            value = shortcut.container.getMIDISoundFont();
                            break;
                        case "screenSize":
                            value = shortcut.container.getScreenSize();
                            break;
                        case "wincomponents":
                            value = shortcut.container.getWinComponents();
                            break;
                        case "box64Version":
                            value = shortcut.container.getBox64Version();
                            break;
                        case "fexcoreVersion":
                            value = shortcut.container.getFEXCoreVersion();
                            break;
                        default:
                            break;
                    }
                }
                if (value != null && !value.isEmpty()) {
                    extraData.put(key, value);
                }
            }

            try {
                java.util.HashMap<String,String> gdc = com.winlator.xmod.contentdialog.GraphicsDriverConfigDialog.parseGraphicsDriverConfig(extraData.optString("graphicsDriverConfig", ""));
                if ("1".equals(gdc.get("adrenotoolsTurnip"))) {
                    String drv = gdc.get("version");
                    if (drv != null && !drv.isEmpty()) extraData.put("adrenotoolsDriverId", drv);
                }
            } catch (Exception ignore) {}

            // Ensure Box64 preset and env vars are exported properly
            try {
                String emulator = extraData.optString("emulator", shortcut.container.getEmulator());
                if ("box64".equalsIgnoreCase(emulator)) {
                    String presetId = extraData.optString("box64Preset", shortcut.container.getBox64Preset());
                    if (presetId != null && !presetId.isEmpty()) {
                        extraData.put("box64Preset", presetId);
                        if (presetId.startsWith(Box64Preset.CUSTOM)) {
                            EnvVars baseEnv = new EnvVars(extraData.optString("envVars", shortcut.container.getEnvVars()));
                            EnvVars presetEnv = Box64PresetManager.getEnvVars("box64", context, presetId);
                            baseEnv.putAll(presetEnv);
                            extraData.put("envVars", baseEnv.toString());
                        }
                    }
                }
            } catch (Exception e) { Log.w(TAG, "Failed to append Box64 preset/env vars", e); }

            configJson.put("settings", extraData);

            try (FileWriter writer = new FileWriter(exportFile)) {
                writer.write(configJson.toString(4));
                writer.flush();
            }

            Log.d(TAG, "Shortcut settings exported to: " + exportFile.getAbsolutePath());
            return exportFile;
        } catch (JSONException | IOException e) {
            Log.e(TAG, "Failed to export shortcut settings", e);
            return null;
        }
    }
    
    /**
     * Imports shortcut settings from a JSON file using URI
     * @param shortcut The shortcut to import settings into
     * @param fileUri The URI of the config file to import from
     * @param context Android context for ContentResolver
     * @return true if successful, false otherwise
     */
    public static boolean importShortcutSettings(Shortcut shortcut, Uri fileUri, Context context) {
        Log.d(TAG, "=== Starting import from URI ===");
        Log.d(TAG, "URI: " + (fileUri != null ? fileUri.toString() : "null"));
        Log.d(TAG, "Shortcut: " + (shortcut != null ? shortcut.name : "null"));
        
        if (fileUri == null || shortcut == null || context == null) {
            Log.e(TAG, "Invalid arguments: uri/shortcut/context is null");
            return false;
        }

        try {
            // Try direct read via ContentResolver
            StringBuilder jsonString = new StringBuilder();
            try (InputStream inputStream = context.getContentResolver().openInputStream(fileUri)) {
                if (inputStream != null) {
                    try (InputStreamReader reader = new InputStreamReader(inputStream, java.nio.charset.StandardCharsets.UTF_8);
                         BufferedReader bufferedReader = new BufferedReader(reader)) {
                        String line;
                        while ((line = bufferedReader.readLine()) != null) jsonString.append(line).append('\n');
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Primary read via ContentResolver failed, will try file fallback", e);
            }

            String jsonContent = jsonString.toString().trim();

            // Fallback: resolve to a real File and reuse file-based importer
            if (jsonContent.isEmpty()) {
                File temp = FileUtils.getFileFromUri(context, fileUri);
                if (temp != null && temp.exists()) {
                    Log.d(TAG, "Using File fallback for import: " + temp.getAbsolutePath());
                    return importShortcutSettings(shortcut, temp);
                } else {
                    Log.e(TAG, "File fallback failed (null or not exists)");
                    return false;
                }
            }

            // Parse JSON
            JSONObject configJson;
            try {
                configJson = new JSONObject(jsonContent);
            } catch (JSONException e) {
                Log.e(TAG, "Invalid JSON format in config file (direct read)", e);
                return false;
            }
            
            // Version: be lenient — import even if unknown version
            String exportVersion = configJson.optString("exportVersion", "1.0");
            if (!isVersionCompatible(exportVersion)) {
                Log.w(TAG, "Config version not recognized: " + exportVersion + ", proceeding leniently");
            }
            
            // Settings object or legacy 'extraData' or top-level as settings
            JSONObject settings = configJson.optJSONObject("settings");
            if (settings == null) {
                settings = configJson.optJSONObject("extraData");
            }
            if (settings == null) {
                Log.w(TAG, "No 'settings' or 'extraData' object found; falling back to top-level keys");
                settings = configJson; // legacy flat format fallback
            }
            
            // Import settings (filtering out fields that should not be imported)
            Iterator<String> keys = settings.keys();
            int importedCount = 0;
            String[] excludedFields = {"customCoverArtPath", "uuid", "path", "shortcutName", "exportVersion", "exportDate", "wmClass", "name"};
            while (keys.hasNext()) {
                String key = keys.next();
                boolean skip = false;
                for (String ex : excludedFields) if (ex.equals(key)) { skip = true; break; }
                if (skip) { Log.d(TAG, "Skipped excluded field: " + key); continue; }
                try {
                    Object val = settings.get(key);
                    String value = String.valueOf(val);
                    if (value != null && !value.isEmpty() && !"null".equalsIgnoreCase(value)) {
                        shortcut.putExtra(key, value);
                        importedCount++;
                        Log.d(TAG, "Imported setting: " + key + " = " + value);
                    }
                } catch (JSONException e) {
                    Log.w(TAG, "Failed to import setting key=" + key, e);
                }
            }
            Log.d(TAG, "Imported " + importedCount + " settings");
            
            try {
                shortcut.saveData();
            } catch (Exception e) {
                Log.e(TAG, "Failed to save shortcut data after import", e);
                return false;
            }
            
            Log.d(TAG, "Shortcut settings imported successfully from URI: " + fileUri);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error during import from URI: " + fileUri, e);
            return false;
        }
    }

    /**
     * Validates if a config file is valid using URI
     */
    public static boolean isValidConfigFile(Uri fileUri, Context context) {
        Log.d(TAG, "=== Validating config file ===");
        Log.d(TAG, "URI: " + (fileUri != null ? fileUri.toString() : "null"));
        
        try {
            if (fileUri == null) {
                Log.e(TAG, "FileUri is null during validation");
                return false;
            }
            
            StringBuilder jsonString = new StringBuilder();
            try (InputStream inputStream = context.getContentResolver().openInputStream(fileUri)) {
                if (inputStream == null) {
                    Log.e(TAG, "Could not open InputStream for validation");
                    return false;
                }
                
                try (InputStreamReader reader = new InputStreamReader(inputStream, "UTF-8");
                     BufferedReader bufferedReader = new BufferedReader(reader)) {
                    
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        jsonString.append(line).append("\n");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reading file for validation", e);
                return false;
            }
            
            String jsonContent = jsonString.toString().trim();
            if (jsonContent.isEmpty()) {
                Log.e(TAG, "File is empty during validation");
                return false;
            }
            
            JSONObject configJson;
            try {
                configJson = new JSONObject(jsonContent);
            } catch (JSONException e) {
                Log.e(TAG, "Invalid JSON during validation", e);
                return false;
            }
            
            // Accept new and legacy formats
            boolean hasSettingsObj = configJson.has("settings") && (configJson.opt("settings") instanceof JSONObject);
            boolean hasExtraDataObj = configJson.has("extraData") && (configJson.opt("extraData") instanceof JSONObject);
            Log.d(TAG, "Validation: hasSettingsObj=" + hasSettingsObj + ", hasExtraDataObj=" + hasExtraDataObj);
            return hasSettingsObj || hasExtraDataObj;
                   
        } catch (Exception e) {
            Log.e(TAG, "Failed to validate config file from URI", e);
            return false;
        }
    }

    /**
     * Gets the shortcut name from a config file using URI
     */
    public static String getShortcutNameFromConfigFile(Uri fileUri, Context context) {
        Log.d(TAG, "=== Getting shortcut name from URI ===");
        Log.d(TAG, "URI: " + (fileUri != null ? fileUri.toString() : "null"));
        
        try {
            if (fileUri == null) {
                Log.e(TAG, "FileUri is null when getting shortcut name");
                return "Unknown";
            }
            
            StringBuilder jsonString = new StringBuilder();
            try (InputStream inputStream = context.getContentResolver().openInputStream(fileUri)) {
                if (inputStream == null) {
                    Log.e(TAG, "Could not open InputStream for getting shortcut name");
                    return "Unknown";
                }
                
                try (InputStreamReader reader = new InputStreamReader(inputStream, "UTF-8");
                     BufferedReader bufferedReader = new BufferedReader(reader)) {
                    
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        jsonString.append(line).append("\n");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reading file for shortcut name", e);
                return "Unknown";
            }
            
            String jsonContent = jsonString.toString().trim();
            if (jsonContent.isEmpty()) {
                Log.e(TAG, "File is empty when getting shortcut name");
                return "Unknown";
            }
            
            Log.d(TAG, "Read JSON content for shortcut name, length: " + jsonContent.length());
            Log.d(TAG, "First 200 chars: " + jsonContent.substring(0, Math.min(200, jsonContent.length())));
            
            JSONObject configJson;
            try {
                configJson = new JSONObject(jsonContent);
            } catch (JSONException e) {
                Log.e(TAG, "Invalid JSON when getting shortcut name", e);
                Log.e(TAG, "JSON content: " + jsonContent.substring(0, Math.min(500, jsonContent.length())));
                return "Unknown";
            }
            
            String shortcutName = configJson.optString("shortcutName", "");
            if (shortcutName == null || shortcutName.isEmpty()) {
                String display = FileUtils.getUriFileName(context, fileUri);
                if (display != null && !display.isEmpty()) {
                    int dot = display.lastIndexOf('.');
                    shortcutName = dot > 0 ? display.substring(0, dot) : display;
                } else {
                    shortcutName = "Unknown";
                }
            }
            Log.d(TAG, "Extracted shortcut name: '" + shortcutName + "'");
            return shortcutName;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to get shortcut name from config file URI", e);
            return "Unknown";
        }
    }

    /**
     * Imports shortcut settings from a JSON file
     * @param shortcut The shortcut to import settings into
     * @param configFile The config file to import from
     * @return true if successful, false otherwise
     */
    public static boolean importShortcutSettings(Shortcut shortcut, File configFile) {
        try {
            if (!configFile.exists() || !configFile.canRead()) {
                Log.e(TAG, "Config file doesn't exist or can't be read: " + configFile.getAbsolutePath());
                return false;
            }
            
            // Read the JSON file
            StringBuilder jsonString = new StringBuilder();
            try (FileReader reader = new FileReader(configFile)) {
                char[] buffer = new char[1024];
                int length;
                while ((length = reader.read(buffer)) != -1) {
                    jsonString.append(buffer, 0, length);
                }
            }
            
            // Parse JSON
            JSONObject configJson = new JSONObject(jsonString.toString());
            
            // Version check is lenient: allow missing or unknown versions
            String exportVersion = configJson.optString("exportVersion", "");
            if (!exportVersion.isEmpty() && !isVersionCompatible(exportVersion)) {
                Log.w(TAG, "Unrecognized config version: " + exportVersion + ", proceeding leniently");
            }
            
            // Import settings from 'settings' or legacy 'extraData' or top-level
            JSONObject settings = configJson.optJSONObject("settings");
            if (settings == null) settings = configJson.optJSONObject("extraData");
            if (settings == null) {
                Log.w(TAG, "No 'settings' or 'extraData' object found; falling back to top-level keys");
                settings = configJson;
            }
            Iterator<String> keys = settings.keys();
            
            // Fields that should NOT be imported as they are specific to the original shortcut
            String[] excludedFields = {"customCoverArtPath", "uuid", "path", "shortcutName", "exportVersion", "exportDate", "wmClass", "name"};
            
            while (keys.hasNext()) {
                String key = keys.next();
                
                // Skip excluded fields
                boolean shouldSkip = false;
                for (String excludedField : excludedFields) {
                    if (key.equals(excludedField)) {
                        shouldSkip = true;
                        Log.d(TAG, "Skipped excluded field: " + key);
                        break;
                    }
                }
                
                if (!shouldSkip) {
                    String value = settings.getString(key);
                    if (value != null && !value.isEmpty()) {
                        shortcut.putExtra(key, value);
                        Log.d(TAG, "Imported setting: " + key + " = " + value);
                    }
                }
            }
            
            // Save the shortcut data
            shortcut.saveData();
            
            Log.d(TAG, "Shortcut settings imported successfully from: " + configFile.getAbsolutePath());
            return true;
            
        } catch (JSONException | IOException e) {
            Log.e(TAG, "Failed to import shortcut settings", e);
            return false;
        }
    }
    
    /**
     * Gets the default export directory
     */
    public static File getExportDirectory() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), EXPORT_DIR);
    }
    
    /**
     * Validates if a config file is a valid shortcut config
     */
    public static boolean isValidConfigFile(File file) {
        if (!file.exists() || !file.canRead()) {
            return false;
        }
        
        if (!file.getName().endsWith(FILE_EXTENSION)) {
            return false;
        }
        
        try {
            StringBuilder jsonString = new StringBuilder();
            try (FileReader reader = new FileReader(file)) {
                char[] buffer = new char[1024];
                int length;
                while ((length = reader.read(buffer)) != -1) {
                    jsonString.append(buffer, 0, length);
                }
            }
            
            JSONObject configJson = new JSONObject(jsonString.toString());
            
            // Accept new and legacy formats
            boolean hasSettingsObj = configJson.has("settings") && (configJson.opt("settings") instanceof JSONObject);
            boolean hasExtraDataObj = configJson.has("extraData") && (configJson.opt("extraData") instanceof JSONObject);
            return hasSettingsObj || hasExtraDataObj;
                   
        } catch (Exception e) {
            Log.e(TAG, "Failed to validate config file", e);
            return false;
        }
    }
    
    /**
     * Gets all valid config files in the export directory
     */
    public static File[] getConfigFiles() {
        File exportDir = getExportDirectory();
        if (!exportDir.exists()) {
            return new File[0];
        }
        
        File[] allFiles = exportDir.listFiles((dir, name) -> name.endsWith(FILE_EXTENSION));
        if (allFiles == null) {
            return new File[0];
        }
        
        // Filter only valid config files
        java.util.List<File> validFiles = new java.util.ArrayList<>();
        for (File file : allFiles) {
            if (isValidConfigFile(file)) {
                validFiles.add(file);
            }
        }
        
        return validFiles.toArray(new File[0]);
    }
    
    /**
     * Checks if the export version is compatible
     */
    private static boolean isVersionCompatible(String version) {
        // For now, only support version 1.0
        // In the future, we can add logic for backwards compatibility
        return "1.0".equals(version);
    }
    
    /**
     * Gets the shortcut name from a config file without fully parsing it
     */
    public static String getShortcutNameFromConfigFile(File configFile) {
        try {
            StringBuilder jsonString = new StringBuilder();
            try (FileReader reader = new FileReader(configFile)) {
                char[] buffer = new char[1024];
                int length;
                while ((length = reader.read(buffer)) != -1) {
                    jsonString.append(buffer, 0, length);
                }
            }
            
            JSONObject configJson = new JSONObject(jsonString.toString());
            return configJson.optString("shortcutName", "Unknown");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to get shortcut name from config file", e);
            return "Unknown";
        }
    }
}