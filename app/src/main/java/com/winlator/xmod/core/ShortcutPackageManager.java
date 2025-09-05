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
import com.winlator.xmod.core.KeyValueSet;
import com.winlator.xmod.core.EnvVars;
import com.winlator.xmod.box64.Box64Preset;
import com.winlator.xmod.box64.Box64PresetManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
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

public class ShortcutPackageManager {
    private static final String TAG = "ShortcutPackageManager";
    private static final String EXPORT_DIR = "Winlator/ShortcutConfigs";
    private static final String FILE_EXTENSION = ".wsc";

    public static class ExportOptions {
        public boolean includeWine = true;
        public boolean includeDXVK = true;
        public boolean includeVKD3D = true;
        public boolean includeBox64 = true;
        public boolean includeWOWBox64 = true;
        public boolean includeFEXCore = true;
        public boolean includeAdrenotools = true;

        public static class Injection {
            public String sourcePath; // absolute path on exporter device
            public String targetPath; // Windows-style path like C:\foo\bar or Z:\foo\bar
            public boolean isDirectory;
            public Injection(String src, String tgt, boolean dir){ this.sourcePath=src; this.targetPath=tgt; this.isDirectory=dir; }
        }
        public final java.util.List<Injection> injections = new java.util.ArrayList<>();
        public void addInjection(String src, String tgt, boolean isDir){ injections.add(new Injection(src, tgt, isDir)); }
    }

    public static File exportShortcutSettings(Shortcut shortcut, Context context, boolean includeContents) {
        if (!includeContents) return ShortcutConfigManager.exportShortcutSettings(shortcut, context);
        return exportShortcutSettings(shortcut, context, new ExportOptions());
    }

    public static File exportShortcutSettings(Shortcut shortcut, Context context, ExportOptions options) {
        try {
            File exportDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), EXPORT_DIR);
            if (!exportDir.exists() && !exportDir.mkdirs()) return null;
            JSONObject configJson = buildConfigJson(shortcut);
            // Ensure Box64 preset and env vars are exported properly
            try {
                JSONObject extraData = configJson.optJSONObject("settings");
                if (extraData == null) extraData = configJson.optJSONObject("extraData");
                if (extraData != null) {
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
                }
            } catch (Exception e) { Log.w(TAG, "Failed to append Box64 preset/env vars (pkg)", e); }
            
            // Append custom file injections metadata if any
            if (options != null && options.injections != null && !options.injections.isEmpty()) {
                org.json.JSONArray injArr = new org.json.JSONArray();
                for (int i = 0; i < options.injections.size(); i++) {
                    ExportOptions.Injection inj = options.injections.get(i);
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("id", "inj" + (i+1));
                    o.put("target", inj.targetPath);
                    o.put("dir", inj.isDirectory);
                    // record original name for reference
                    try { o.put("name", new java.io.File(inj.sourcePath).getName()); } catch (Exception ignore) {}
                    injArr.put(o);
                }
                configJson.put("injections", injArr);
            }
            File zipFile = new File(exportDir, shortcut.name + ".zip");
            try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)))) {
                ZipEntry cfg = new ZipEntry("settings.wsc");
                zos.putNextEntry(cfg);
                byte[] cfgBytes = configJson.toString(4).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                zos.write(cfgBytes);
                zos.closeEntry();
                for (ZipContent zc : gatherUsedContents(shortcut, context, options)) zipDirectory(zc.dir, zc.zipPathPrefix, zos);
                // Add injections payloads
                if (options != null && options.injections != null) {
                    for (int i = 0; i < options.injections.size(); i++) {
                        ExportOptions.Injection inj = options.injections.get(i);
                        File src = new File(inj.sourcePath);
                        String base = "injections/inj" + (i+1);
                        if (inj.isDirectory) {
                            if (src.isDirectory()) zipDirectory(src, base + "/", zos);
                        } else {
                            if (src.isFile()) {
                                zos.putNextEntry(new ZipEntry(base));
                                try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(src))) {
                                    byte[] buf = new byte[8192]; int r; while ((r = in.read(buf)) != -1) zos.write(buf, 0, r);
                                }
                                zos.closeEntry();
                            }
                        }
                    }
                }
            }
            return zipFile;
        } catch (Exception e) {
            Log.e(TAG, "export with contents failed", e);
            return null;
        }
    }

    public static boolean isValid(Uri fileUri, Context context) {
        if (fileUri == null) return false;
        try {
            // Try as JSON
            try (InputStream inputStream = context.getContentResolver().openInputStream(fileUri)) {
                if (inputStream != null) {
                    try (InputStreamReader reader = new InputStreamReader(inputStream, "UTF-8"); BufferedReader br = new BufferedReader(reader)) {
                        final int LIMIT = 1024 * 1024; // 1MB
                        StringBuilder sb = new StringBuilder();
                        char[] buf = new char[4096]; int r; int total = 0;
                        while ((r = br.read(buf)) != -1 && total < LIMIT) { sb.append(buf, 0, r); total += r; }
                        String json = sb.toString().trim();
                        if (!json.isEmpty()) {
                            JSONObject o = new JSONObject(json);
                            return (o.has("settings") && o.opt("settings") instanceof JSONObject) || (o.has("extraData") && o.opt("extraData") instanceof JSONObject);
                        }
                    }
                }
            } catch (Exception ignore) {}

            // Try as ZIP
            File tmp = FileUtils.getFileFromUri(context, fileUri);
            if (tmp == null || !tmp.exists()) return false;
            try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(tmp)))) {
                ZipEntry e;
                final int LIMIT = 1024 * 1024; // 1MB
                while ((e = zis.getNextEntry()) != null) {
                    String name = e.getName();
                    if (e.isDirectory()) { zis.closeEntry(); continue; }
                    if (!(name.endsWith(".wsc") || name.endsWith(".json"))) { zis.closeEntry(); continue; }
                    if (e.getSize() > 5L * 1024 * 1024) { zis.closeEntry(); continue; } // skip huge entries
                    StringBuilder sb = new StringBuilder();
                    byte[] buf = new byte[4096]; int r; int total = 0;
                    while ((r = zis.read(buf)) != -1 && total < LIMIT) { sb.append(new String(buf, 0, r, java.nio.charset.StandardCharsets.UTF_8)); total += r; }
                    try {
                        JSONObject o = new JSONObject(sb.toString());
                        boolean ok = (o.has("settings") && o.opt("settings") instanceof JSONObject) || (o.has("extraData") && o.opt("extraData") instanceof JSONObject);
                        if (ok) return true;
                    } catch (JSONException je) { /* ignore and continue */ }
                    zis.closeEntry();
                }
            } catch (IOException ioe) { return false; }
        } catch (Exception e) {
            Log.e(TAG, "isValid failed", e);
        }
        return false;
    }

    public static String getName(Uri fileUri, Context context) {
        if (fileUri == null) return "Unknown";
        try {
            // JSON direct
            try (InputStream inputStream = context.getContentResolver().openInputStream(fileUri)) {
                if (inputStream != null) {
                    try (InputStreamReader reader = new InputStreamReader(inputStream, "UTF-8"); BufferedReader br = new BufferedReader(reader)) {
                        final int LIMIT = 1024 * 1024; // 1MB
                        StringBuilder sb = new StringBuilder(); char[] cbuf = new char[4096]; int r, total=0;
                        while ((r = br.read(cbuf)) != -1 && total < LIMIT) { sb.append(cbuf, 0, r); total += r; }
                        String json = sb.toString().trim();
                        if (!json.isEmpty()) { JSONObject o = new JSONObject(json); String name = o.optString("shortcutName", ""); if (!name.isEmpty()) return name; }
                    }
                }
            } catch (Exception ignore) {}
            // ZIP
            File tmp = FileUtils.getFileFromUri(context, fileUri);
            if (tmp == null || !tmp.exists()) return "Unknown";
            try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(tmp)))) {
                ZipEntry e; byte[] buf = new byte[4096]; final int LIMIT = 1024 * 1024;
                while ((e = zis.getNextEntry()) != null) {
                    String name = e.getName();
                    if (e.isDirectory()) { zis.closeEntry(); continue; }
                    if (!(name.endsWith(".wsc") || name.endsWith(".json"))) { zis.closeEntry(); continue; }
                    if (e.getSize() > 5L * 1024 * 1024) { zis.closeEntry(); continue; }
                    StringBuilder sb = new StringBuilder(); int r, total=0; while ((r = zis.read(buf)) != -1 && total < LIMIT) { sb.append(new String(buf,0,r, java.nio.charset.StandardCharsets.UTF_8)); total += r; }
                    try { JSONObject o = new JSONObject(sb.toString()); String sname = o.optString("shortcutName", ""); if (!sname.isEmpty()) return sname; } catch (JSONException je) {}
                    zis.closeEntry();
                }
            } catch (IOException io) { return "Unknown"; }
        } catch (Exception e) { Log.e(TAG, "getName failed", e); }
        return "Unknown";
    }

    public static boolean importFromUri(Shortcut shortcut, Uri fileUri, Context context) {
        if (fileUri == null) return false;
        try {
            String fileName = FileUtils.getUriFileName(context, fileUri);
            String lowerName = fileName != null ? fileName.toLowerCase() : "";

            if (lowerName.endsWith(".zip")) {
                File tmpZip = FileUtils.getFileFromUri(context, fileUri);
                if (tmpZip == null || !tmpZip.exists()) return false;
                return importFromZip(shortcut, tmpZip, context);
            }

            final int CHAR_LIMIT = 1024 * 1024;
            try (InputStream inputStream = context.getContentResolver().openInputStream(fileUri)) {
                if (inputStream != null) {
                    try (InputStreamReader reader = new InputStreamReader(inputStream, java.nio.charset.StandardCharsets.UTF_8);
                         BufferedReader br = new BufferedReader(reader)) {
                        StringBuilder sb = new StringBuilder();
                        String l; int total = 0;
                        while ((l = br.readLine()) != null) {
                            sb.append(l).append('\n');
                            total += l.length() + 1;
                            if (total >= CHAR_LIMIT) { break; }
                        }
                        String json = sb.toString().trim();
                        if (!json.isEmpty()) {
                            try {
                                JSONObject o = new JSONObject(json);
                                applySettingsJson(shortcut, o);
                                return true;
                            } catch (JSONException je) {
                                // Not JSON, will try file-based fallback next
                            }
                        }
                    }
                }
            } catch (Throwable ignore) { }

            File tmp = FileUtils.getFileFromUri(context, fileUri);
            if (tmp == null || !tmp.exists()) return false;
            String name = tmp.getName().toLowerCase();
            if (name.endsWith(FILE_EXTENSION) || name.endsWith(".json")) {
                return ShortcutConfigManager.importShortcutSettings(shortcut, tmp);
            }
            return importFromZip(shortcut, tmp, context);
        } catch (Exception e) {
            Log.e(TAG, "importFromUri failed", e);
            return false;
        }
    }

    private static JSONObject buildConfigJson(Shortcut shortcut) throws JSONException {
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
            if (value != null && !value.isEmpty()) extraData.put(key, value);
        }
        try {
            java.util.HashMap<String,String> gdc = com.winlator.xmod.contentdialog.GraphicsDriverConfigDialog.parseGraphicsDriverConfig(extraData.optString("graphicsDriverConfig", ""));
            if ("1".equals(gdc.get("adrenotoolsTurnip"))) {
                String drv = gdc.get("version");
                if (drv != null && !drv.isEmpty()) extraData.put("adrenotoolsDriverId", drv);
            }
        } catch (Exception ignore) {}
        configJson.put("settings", extraData);
        return configJson;
    }

    private static class ZipContent { final File dir; final String zipPathPrefix; ZipContent(File d, String p){dir=d;zipPathPrefix=p;} }

    private static List<ZipContent> gatherUsedContents(Shortcut shortcut, Context context, ExportOptions options) {
        List<ZipContent> list = new ArrayList<>();
        try {
            Container container = shortcut.container;
            ContentsManager cm = new ContentsManager(context); cm.syncContents();
            // Wine/Proton
            ContentProfile wineProfile = cm.getProfileByEntryName(container.getWineVersion());
            if (wineProfile != null && options.includeWine) {
                File install = ContentsManager.getInstallDir(context, wineProfile);
                if (install.exists()) list.add(new ZipContent(install, "contents/" + wineProfile.type + "/" + install.getName() + "/"));
            }
            // DXVK / VKD3D
            String dxw = shortcut.getExtra("dxwrapper", container.getDXWrapper());
            String dxcfg = shortcut.getExtra("dxwrapperConfig", container.getDXWrapperConfig());
            KeyValueSet dxkv = DXVKConfigDialog.parseConfig(dxcfg); KeyValueSet vkdv = VKD3DConfigDialog.parseConfig(dxcfg);
            if ("dxvk".equalsIgnoreCase(dxw) && options.includeDXVK) {
                String ver = dxkv.get("version");
                if (!ver.isEmpty()) {
                    File base = ContentsManager.getContentTypeDir(context, ContentProfile.ContentType.CONTENT_TYPE_DXVK);
                    File dir = new File(base, ver);
                    if (dir.exists()) list.add(new ZipContent(dir, "contents/DXVK/" + ver + "/"));
                }
            } else if ("vkd3d".equalsIgnoreCase(dxw) && options.includeVKD3D) {
                String ver = vkdv.get("vkd3dVersion");
                if (!ver.isEmpty()) {
                    File base = ContentsManager.getContentTypeDir(context, ContentProfile.ContentType.CONTENT_TYPE_VKD3D);
                    File dir = new File(base, ver);
                    if (dir.exists()) list.add(new ZipContent(dir, "contents/VKD3D/" + ver + "/"));
                }
            } else if ("unified".equalsIgnoreCase(dxw)) {
                // Handle unified mode (DXVK + VKD3D)
                if (options.includeDXVK) {
                    String dxvkVer = dxkv.get("version");
                    if (!dxvkVer.isEmpty()) {
                        File dxvkBase = ContentsManager.getContentTypeDir(context, ContentProfile.ContentType.CONTENT_TYPE_DXVK);
                        File dxvkDir = new File(dxvkBase, dxvkVer);
                        if (dxvkDir.exists()) list.add(new ZipContent(dxvkDir, "contents/DXVK/" + dxvkVer + "/"));
                    }
                }
                if (options.includeVKD3D) {
                    String vkd3dVer = vkdv.get("vkd3dVersion");
                    if (!vkd3dVer.isEmpty()) {
                        File vkd3dBase = ContentsManager.getContentTypeDir(context, ContentProfile.ContentType.CONTENT_TYPE_VKD3D);
                        File vkd3dDir = new File(vkd3dBase, vkd3dVer);
                        if (vkd3dDir.exists()) list.add(new ZipContent(vkd3dDir, "contents/VKD3D/" + vkd3dVer + "/"));
                    }
                }
            }
            // Box64 / WOWBox64
            String box64Version = container.getBox64Version();
            if (box64Version != null && !box64Version.isEmpty()) {
                if (options.includeBox64) {
                    File baseBox64 = ContentsManager.getContentTypeDir(context, ContentProfile.ContentType.CONTENT_TYPE_BOX64);
                    File d1 = new File(baseBox64, box64Version);
                    if (d1.exists()) list.add(new ZipContent(d1, "contents/Box64/" + box64Version + "/"));
                }
                if (options.includeWOWBox64) {
                    File baseWow = ContentsManager.getContentTypeDir(context, ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64);
                    File d2 = new File(baseWow, box64Version);
                    if (d2.exists()) list.add(new ZipContent(d2, "contents/WOWBox64/" + box64Version + "/"));
                }
            }
            // FEXCore
            if (options.includeFEXCore) {
                String fex = container.getFEXCoreVersion();
                if (fex != null && !fex.isEmpty()) {
                    File base = ContentsManager.getContentTypeDir(context, ContentProfile.ContentType.CONTENT_TYPE_FEXCORE);
                    File dir = new File(base, fex);
                    if (dir.exists()) list.add(new ZipContent(dir, "contents/FEXCore/" + fex + "/"));
                }
            }
            // Adrenotools
            if (options.includeAdrenotools) {
                String gdc = shortcut.getExtra("graphicsDriverConfig", container.getGraphicsDriverConfig());
                java.util.HashMap<String, String> gdkv = GraphicsDriverConfigDialog.parseGraphicsDriverConfig(gdc);
                if ("1".equals(gdkv.get("adrenotoolsTurnip"))) {
                    String ver = gdkv.get("version");
                    if (ver != null && !ver.isEmpty()) {
                        AdrenotoolsManager am = new AdrenotoolsManager(context);
                        if (am.enumarateInstalledDrivers().contains(ver)) {
                            File dir = new File(context.getFilesDir(), "contents/adrenotools/" + ver);
                            if (dir.exists()) list.add(new ZipContent(dir, "contents/adrenotools/" + ver + "/"));
                        }
                    }
                }
            }
        } catch (Exception e) { Log.w(TAG, "gatherUsedContents", e); }
        return list;
    }

    private static void zipDirectory(File dir, String zipPathPrefix, ZipOutputStream zos) throws IOException {
        Deque<File> stack = new ArrayDeque<>(); String basePath = dir.getAbsolutePath(); stack.push(dir);
        while (!stack.isEmpty()) {
            File current = stack.pop(); File[] files = current.listFiles(); if (files == null) continue;
            for (File f : files) {
                String rel = f.getAbsolutePath().substring(basePath.length()); if (rel.startsWith(File.separator)) rel = rel.substring(1);
                String entryName = zipPathPrefix + rel.replace(File.separatorChar, '/');
                if (f.isDirectory()) { stack.push(f); if (!entryName.endsWith("/")) entryName += "/"; zos.putNextEntry(new ZipEntry(entryName)); zos.closeEntry(); }
                else { zos.putNextEntry(new ZipEntry(entryName)); try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(f))) { byte[] buf = new byte[8192]; int r; while ((r = in.read(buf)) != -1) zos.write(buf, 0, r); } zos.closeEntry(); }
            }
        }
    }

    private static void applySettingsJson(Shortcut shortcut, JSONObject configJson) throws JSONException {
        String exportVersion = configJson.optString("exportVersion", "1.0");
        if (!"1.0".equals(exportVersion)) Log.w(TAG, "Unknown version " + exportVersion + ", proceeding");
        JSONObject settings = configJson.optJSONObject("settings"); if (settings == null) settings = configJson.optJSONObject("extraData"); if (settings == null) settings = configJson;
        Iterator<String> keys = settings.keys();
        String[] excludedFields = {"customCoverArtPath", "uuid", "path", "shortcutName", "exportVersion", "exportDate", "wmClass", "name"};
        while (keys.hasNext()) { String key = keys.next(); boolean skip = false; for (String ex : excludedFields) if (ex.equals(key)) { skip = true; break; } if (skip) continue; String value = String.valueOf(settings.get(key)); if (value != null && !value.isEmpty() && !"null".equalsIgnoreCase(value)) shortcut.putExtra(key, value); }
        shortcut.saveData();
    }

    private static boolean importFromZip(Shortcut shortcut, File zipFile, Context context) {
        File tmpDir = new File(context.getFilesDir(), "tmp/import_shortcut_pkg"); FileUtils.delete(tmpDir); tmpDir.mkdirs();
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry entry; byte[] buf = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                File out = new File(tmpDir, entry.getName());
                if (entry.isDirectory()) { out.mkdirs(); }
                else { File parent = out.getParentFile(); if (!parent.exists()) parent.mkdirs(); try (FileOutputStream fos = new FileOutputStream(out)) { int r; while ((r = zis.read(buf)) != -1) fos.write(buf, 0, r); } }
                zis.closeEntry();
            }
        } catch (IOException e) { Log.e(TAG, "unzip failed", e); FileUtils.delete(tmpDir); return false; }
        File configFile = findFirstFileWithSuffix(tmpDir, ".wsc"); if (configFile == null) configFile = findFirstFileWithSuffix(tmpDir, ".json"); if (configFile == null) { FileUtils.delete(tmpDir); return false; }
        File contentsRoot = new File(tmpDir, "contents"); if (contentsRoot.exists() && contentsRoot.isDirectory()) installContentsFromFolder(context, contentsRoot);
        // Handle custom file injections
        try {
            String json = FileUtils.readString(configFile);
            JSONObject cfg = new JSONObject(json);
            org.json.JSONArray injArr = cfg.optJSONArray("injections");
            if (injArr != null) {
                com.winlator.xmod.xenvironment.ImageFs imageFs = com.winlator.xmod.xenvironment.ImageFs.find(context);
                File rootDir = imageFs.getRootDir();
                File driveC = new File(rootDir, com.winlator.xmod.xenvironment.ImageFs.WINEPREFIX + "/drive_c");
                File zBase = rootDir.getParentFile() != null && rootDir.getParentFile().getParentFile() != null ? rootDir.getParentFile().getParentFile() : rootDir;
                for (int i = 0; i < injArr.length(); i++) {
                    JSONObject it = injArr.getJSONObject(i);
                    String id = it.optString("id");
                    String target = it.optString("target");
                    boolean isDir = it.optBoolean("dir", false);
                    if (id == null || id.isEmpty() || target == null || target.isEmpty()) continue;
                    File src = new File(new File(tmpDir, "injections"), id);
                    // Map target Windows-like path to host path
                    File destBase;
                    String rest;
                    String tUpper = target.replace('/', '\\');
                    if (tUpper.length() >= 2 && (tUpper.startsWith("C:") || tUpper.startsWith("c:"))) {
                        destBase = driveC;
                        rest = tUpper.substring(2); // after 'C:'
                    } else if (tUpper.length() >= 2 && (tUpper.startsWith("Z:") || tUpper.startsWith("z:"))) {
                        destBase = zBase;
                        rest = tUpper.substring(2);
                    } else {
                        // default to C:
                        destBase = driveC; rest = tUpper;
                    }
                    // normalize backslashes to file separators and trim leading separators
                    String rel = rest.replace('\\', File.separatorChar);
                    while (rel.startsWith(File.separator)) rel = rel.substring(1);
                    File destPath = new File(destBase, rel);
                    if (isDir) {
                        FileUtils.copy(src, destPath);
                    } else {
                        // src may be a file named 'injN'
                        if (src.isFile()) {
                            File parent = destPath.getParentFile(); if (parent != null && !parent.exists()) parent.mkdirs();
                            try (java.io.FileInputStream in = new java.io.FileInputStream(src); java.io.FileOutputStream out = new java.io.FileOutputStream(destPath)) {
                                byte[] buf = new byte[8192]; int r; while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) { Log.w(TAG, "injections import failed", e); }
        boolean ok = ShortcutConfigManager.importShortcutSettings(shortcut, configFile); FileUtils.delete(tmpDir); return ok;
    }

    private static File findFirstFileWithSuffix(File dir, String suffix) {
        Deque<File> dq = new ArrayDeque<>(); dq.add(dir);
        while (!dq.isEmpty()) { File f = dq.removeFirst(); File[] children = f.listFiles(); if (children == null) continue; for (File c : children) { if (c.isDirectory()) dq.addLast(c); else if (c.getName().toLowerCase().endsWith(suffix)) return c; } }
        return null;
    }

    private static void installContentsFromFolder(Context context, File contentsRoot) {
        String[] types = new String[]{"Wine","Proton","DXVK","VKD3D","Box64","WOWBox64","FEXCore","adrenotools"};
        for (String type : types) {
            File typeDir = new File(contentsRoot, type);
            if (!typeDir.exists() || !typeDir.isDirectory()) continue;
            File[] versions = typeDir.listFiles();
            if (versions == null) continue;
            for (File versionDir : versions) {
                if (!versionDir.isDirectory()) continue;
                File target;
                if ("adrenotools".equalsIgnoreCase(type))
                    target = new File(context.getFilesDir(), "contents/adrenotools/" + versionDir.getName());
                else
                    target = new File(ContentsManager.getContentDir(context), type + "/" + versionDir.getName());
                if (!target.exists()) {
                    FileUtils.copy(versionDir, target);
                } else {
                    Log.d(TAG, "Content already installed, skipping: " + type + "/" + versionDir.getName());
                }
            }
        }
        try { new ContentsManager(context).syncContents(); } catch (Exception ignored) {}
    }
}
