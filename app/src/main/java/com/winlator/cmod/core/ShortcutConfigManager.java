package com.winlator.cmod.core;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import com.winlator.cmod.container.Shortcut;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Iterator;

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
            // Create export directory if it doesn't exist
            File exportDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), EXPORT_DIR);
            if (!exportDir.exists() && !exportDir.mkdirs()) {
                Log.e(TAG, "Failed to create export directory");
                return null;
            }

            // Create the export file
            File exportFile = new File(exportDir, shortcut.name + FILE_EXTENSION);
            
            // Create JSON object with shortcut settings
            JSONObject configJson = new JSONObject();
            
            // Basic shortcut info (removed path as it's specific to original shortcut)
            configJson.put("shortcutName", shortcut.name);
            configJson.put("wmClass", shortcut.wmClass);
            configJson.put("exportVersion", "1.0");
            configJson.put("exportDate", System.currentTimeMillis());
            
            // Export all extra data (settings)
            JSONObject extraData = new JSONObject();
            
            // Get all the shortcut extra settings (excluding path-specific fields)
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
                if (!value.isEmpty()) {
                    extraData.put(key, value);
                }
            }
            
            configJson.put("settings", extraData);
            
            // Write to file
            try (FileWriter writer = new FileWriter(exportFile)) {
                writer.write(configJson.toString(4)); // Pretty print with 4 spaces indentation
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
        
        try {
            // Check if we can access the URI
            if (fileUri == null) {
                Log.e(TAG, "FileUri is null");
                return false;
            }
            
            if (shortcut == null) {
                Log.e(TAG, "Shortcut is null");
                return false;
            }
            
            // Read the JSON file using ContentResolver
            StringBuilder jsonString = new StringBuilder();
            try (InputStream inputStream = context.getContentResolver().openInputStream(fileUri)) {
                if (inputStream == null) {
                    Log.e(TAG, "Could not open InputStream for URI: " + fileUri.toString());
                    return false;
                }
                
                try (InputStreamReader reader = new InputStreamReader(inputStream, "UTF-8");
                     BufferedReader bufferedReader = new BufferedReader(reader)) {
                    
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        jsonString.append(line).append("\n");
                    }
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Permission denied accessing URI: " + fileUri.toString(), e);
                return false;
            } catch (IOException e) {
                Log.e(TAG, "IO error reading from URI: " + fileUri.toString(), e);
                return false;
            }
            
            String jsonContent = jsonString.toString().trim();
            if (jsonContent.isEmpty()) {
                Log.e(TAG, "File content is empty");
                return false;
            }
            
            Log.d(TAG, "Successfully read file content, length: " + jsonContent.length());
            
            // Parse JSON
            JSONObject configJson;
            try {
                configJson = new JSONObject(jsonContent);
            } catch (JSONException e) {
                Log.e(TAG, "Invalid JSON format in config file", e);
                return false;
            }
            
            // Validate version (for future compatibility)
            String exportVersion = configJson.optString("exportVersion", "1.0");
            if (!isVersionCompatible(exportVersion)) {
                Log.e(TAG, "Incompatible config file version: " + exportVersion);
                return false;
            }
            
            // Check if settings object exists
            if (!configJson.has("settings")) {
                Log.e(TAG, "Config file does not contain settings object");
                return false;
            }
            
            // Import settings (filtering out fields that should not be imported)
            JSONObject settings = configJson.getJSONObject("settings");
            Iterator<String> keys = settings.keys();
            int importedCount = 0;
            
            // Fields that should NOT be imported as they are specific to the original shortcut
            String[] excludedFields = {"customCoverArtPath", "uuid", "path"};
            
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
                    try {
                        String value = settings.getString(key);
                        if (value != null && !value.isEmpty()) {
                            shortcut.putExtra(key, value);
                            importedCount++;
                            Log.d(TAG, "Imported setting: " + key + " = " + value);
                        }
                    } catch (JSONException e) {
                        Log.w(TAG, "Failed to import setting: " + key, e);
                    }
                }
            }
            
            Log.d(TAG, "Imported " + importedCount + " settings");
            
            // Save the shortcut data
            try {
                shortcut.saveData();
            } catch (Exception e) {
                Log.e(TAG, "Failed to save shortcut data after import", e);
                return false;
            }
            
            Log.d(TAG, "Shortcut settings imported successfully from URI: " + fileUri.toString());
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error during import from URI: " + fileUri.toString(), e);
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
            
            // Check if it has required fields
            boolean hasShortcutName = configJson.has("shortcutName");
            boolean hasSettings = configJson.has("settings");
            boolean hasExportVersion = configJson.has("exportVersion");
            
            Log.d(TAG, "Validation: shortcutName=" + hasShortcutName + ", settings=" + hasSettings + ", exportVersion=" + hasExportVersion);
            
            return hasShortcutName && hasSettings && hasExportVersion;
                   
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
            
            String shortcutName = configJson.optString("shortcutName", "Unknown");
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
            
            // Validate version (for future compatibility)
            String exportVersion = configJson.optString("exportVersion", "1.0");
            if (!isVersionCompatible(exportVersion)) {
                Log.e(TAG, "Incompatible config file version: " + exportVersion);
                return false;
            }
            
            // Import settings (filtering out fields that should not be imported)
            JSONObject settings = configJson.getJSONObject("settings");
            Iterator<String> keys = settings.keys();
            
            // Fields that should NOT be imported as they are specific to the original shortcut
            String[] excludedFields = {"customCoverArtPath", "uuid", "path"};
            
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
            
            // Check if it has required fields
            return configJson.has("shortcutName") && 
                   configJson.has("settings") && 
                   configJson.has("exportVersion");
                   
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