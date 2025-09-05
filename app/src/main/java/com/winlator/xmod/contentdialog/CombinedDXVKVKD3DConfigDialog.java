package com.winlator.xmod.contentdialog;

import android.content.Context;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.ToggleButton;

import com.winlator.xmod.R;
import com.winlator.xmod.contents.ContentProfile;
import com.winlator.xmod.contents.ContentsManager;
import com.winlator.xmod.core.AppUtils;
import com.winlator.xmod.core.KeyValueSet;
import com.winlator.xmod.core.StringUtils;
import com.winlator.xmod.core.VKD3DVersionItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CombinedDXVKVKD3DConfigDialog extends ContentDialog {
    private final Context context;
    private final boolean isARM64EC;

    public CombinedDXVKVKD3DConfigDialog(View anchor, boolean isARM64EC) {
        super(anchor.getContext(), R.layout.dxvk_vkd3d_config_dialog);
        this.context = anchor.getContext();
        this.isARM64EC = isARM64EC;

        setIcon(R.drawable.icon_settings);
        setTitle("DXVK + VKD3D " + context.getString(R.string.configuration));

        final Spinner sVersionDXVK = findViewById(R.id.SVersionDXVK);
        final Spinner sFramerateDXVK = findViewById(R.id.SFramerateDXVK);
        final Spinner sMaxDeviceMemoryDXVK = findViewById(R.id.SMaxDeviceMemoryDXVK);
        final ToggleButton swAsyncDXVK = findViewById(R.id.SWAsyncDXVK);
        final ToggleButton swAsyncCacheDXVK = findViewById(R.id.SWAsyncCacheDXVK);
        final View llAsyncDXVK = findViewById(R.id.LLAsyncDXVK);
        final View llAsyncCacheDXVK = findViewById(R.id.LLAsyncCacheDXVK);

        final Spinner sVersionVKD3D = findViewById(R.id.SVersionVKD3D);
        final Spinner sFeatureLevelVKD3D = findViewById(R.id.SFeatureLevelVKD3D);

        ContentsManager contentsManager = new ContentsManager(context);
        contentsManager.syncContents();

        List<String> dxvkVersions = loadDxvkVersions(contentsManager);
        sVersionDXVK.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, dxvkVersions));

        List<VKD3DVersionItem> vkd3dVersions = loadVkd3dVersions(contentsManager);
        ArrayAdapter<VKD3DVersionItem> vkd3dAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, vkd3dVersions);
        sVersionVKD3D.setAdapter(vkd3dAdapter);

        ArrayAdapter<String> featureAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, VKD3DConfigDialog.VKD3D_FEATURE_LEVEL);
        featureAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sFeatureLevelVKD3D.setAdapter(featureAdapter);

        KeyValueSet config = DXVKConfigDialog.parseConfig(anchor.getTag());
        AppUtils.setSpinnerSelectionFromIdentifier(sVersionDXVK, config.get("version"));
        AppUtils.setSpinnerSelectionFromIdentifier(sFramerateDXVK, config.get("framerate"));
        AppUtils.setSpinnerSelectionFromNumber(sMaxDeviceMemoryDXVK, config.get("maxDeviceMemory"));
        swAsyncDXVK.setChecked(config.get("async").equals("1"));
        swAsyncCacheDXVK.setChecked(config.get("asyncCache").equals("1"));

        setDxvkVisibility(dxvkVersions, sVersionDXVK.getSelectedItemPosition(), llAsyncDXVK, llAsyncCacheDXVK);
        sVersionDXVK.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                setDxvkVisibility(dxvkVersions, position, llAsyncDXVK, llAsyncCacheDXVK);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        setSpinnerSelectionByIdentifier(sVersionVKD3D, config.get("vkd3dVersion"));
        AppUtils.setSpinnerSelectionFromIdentifier(sFeatureLevelVKD3D, config.get("vkd3dLevel"));

        setOnConfirmCallback(() -> {
            config.put("version", sVersionDXVK.getSelectedItem().toString());
            config.put("framerate", StringUtils.parseNumber(sFramerateDXVK.getSelectedItem()));
            config.put("maxDeviceMemory", StringUtils.parseNumber(sMaxDeviceMemoryDXVK.getSelectedItem()));
            config.put("async", (swAsyncDXVK.isChecked() && llAsyncDXVK.getVisibility()==View.VISIBLE)?"1":"0");
            config.put("asyncCache", (swAsyncCacheDXVK.isChecked() && llAsyncCacheDXVK.getVisibility()==View.VISIBLE)?"1":"0");

            VKD3DVersionItem vkd3dItem = (VKD3DVersionItem) sVersionVKD3D.getSelectedItem();
            config.put("vkd3dVersion", vkd3dItem.getIdentifier());
            config.put("vkd3dLevel", sFeatureLevelVKD3D.getSelectedItem().toString());

            anchor.setTag(config.toString());
        });
    }

    private void setSpinnerSelectionByIdentifier(Spinner spinner, String identifier) {
        for (int i = 0; i < spinner.getCount(); i++) {
            Object item = spinner.getItemAtPosition(i);
            if (item instanceof VKD3DVersionItem) {
                if (((VKD3DVersionItem) item).getIdentifier().equals(identifier)) {
                    spinner.setSelection(i);
                    break;
                }
            }
        }
    }

    private void setDxvkVisibility(List<String> dxvkVersions, int pos, View llAsync, View llAsyncCache) {
        String v = dxvkVersions.get(pos);
        boolean gpl = v.contains("gplasync");
        boolean async = v.contains("async") || gpl;
        llAsync.setVisibility(async ? View.VISIBLE : View.GONE);
        llAsyncCache.setVisibility(gpl ? View.VISIBLE : View.GONE);
    }

    private List<String> loadDxvkVersions(ContentsManager manager) {
        String[] originalItems = context.getResources().getStringArray(R.array.dxvk_version_entries);
        List<String> itemList = new ArrayList<>(Arrays.asList(originalItems));
        for (ContentProfile profile : manager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_DXVK)) {
            String entryName = ContentsManager.getEntryName(profile);
            int firstDashIndex = entryName.indexOf('-');
            itemList.add(entryName.substring(firstDashIndex + 1));
        }
        for (int i = 0; i < itemList.size(); i++) {
            if (itemList.get(i).contains("arm64ec") && !isARM64EC) {
                itemList.remove(i);
                i--;
            }
        }
        return itemList;
    }

    private List<VKD3DVersionItem> loadVkd3dVersions(ContentsManager manager) {
        List<VKD3DVersionItem> itemList = new ArrayList<>();
        String[] originalItems = context.getResources().getStringArray(R.array.vkd3d_version_entries);
        for (String version : originalItems) itemList.add(new VKD3DVersionItem(version));
        for (ContentProfile profile : manager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_VKD3D)) {
            String displayName = profile.verName;
            int versionCode = profile.verCode;
            itemList.add(new VKD3DVersionItem(displayName, versionCode));
        }
        return itemList;
    }
}
