package com.winlator.xmod;

import static androidx.core.content.ContextCompat.getSystemService;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.Manifest;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.winlator.xmod.R;
import com.winlator.xmod.container.Container;
import com.winlator.xmod.container.ContainerManager;
import com.winlator.xmod.container.Shortcut;
import com.winlator.xmod.contentdialog.ContentDialog;
import com.winlator.xmod.contentdialog.FilePickerDialog;
import com.winlator.xmod.contentdialog.ShortcutSettingsDialog;
import com.winlator.xmod.core.FileUtils;
import com.winlator.xmod.core.ShortcutConfigManager;
import com.winlator.xmod.core.PreloaderDialog;
import com.winlator.xmod.core.ShortcutPackageManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ShortcutsFragment extends Fragment {
    private RecyclerView recyclerView;
    private TextView emptyTextView;
    private ContainerManager manager;
    private static final int REQUEST_IMAGE_GALLERY = 1001;
    private static final int REQUEST_IMAGE_CAMERA = 1002;
    private static final int REQUEST_CAMERA_PERMISSION = 1003;
    private static final int REQUEST_FILE_PICKER = 1004;
    private static final int REQUEST_EXECUTABLE_PICKER = 1005;
    private static final int REQUEST_NEW_SHORTCUT_COVER = 1010;
    private static final int REQUEST_ADD_INJECTION_FILE = 1101;
    private static final int REQUEST_ADD_INJECTION_FOLDER = 1102;
    private Shortcut currentShortcutForCover;
    private Shortcut currentShortcutForImport;
    private com.winlator.xmod.core.ShortcutPackageManager.ExportOptions pendingExportOptions;
    private boolean pendingInjectionIsDir;
    private Container selectedContainer;

    private android.graphics.Bitmap pendingCoverBitmap;
    private android.widget.ImageView pendingCoverPreview;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        manager = new ContainerManager(getContext());
        loadShortcutsList();
        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(R.string.shortcuts);
        
        FloatingActionButton fabAddShortcut = view.findViewById(R.id.fab_add_shortcut);
        fabAddShortcut.setOnClickListener(v -> showContainerSelectionForNewShortcut());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {



        FrameLayout frameLayout = (FrameLayout)inflater.inflate(R.layout.shortcuts_fragment, container, false);
        recyclerView = frameLayout.findViewById(R.id.RecyclerView);
        emptyTextView = frameLayout.findViewById(R.id.TVEmptyText);
        recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
        recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(), DividerItemDecoration.VERTICAL));
        return frameLayout;
    }

    public void loadShortcutsList() {
        ArrayList<Shortcut> shortcuts = manager.loadShortcuts();

        // Validate and remove corrupted shortcuts
        shortcuts.removeIf(shortcut -> shortcut == null || shortcut.file == null || shortcut.file.getName().isEmpty());

        recyclerView.setAdapter(new ShortcutsAdapter(shortcuts));
        if (shortcuts.isEmpty()) emptyTextView.setVisibility(View.VISIBLE);
        else emptyTextView.setVisibility(View.GONE); // Ensure the empty text view is hidden if there are shortcuts
    }


    private class ShortcutsAdapter extends RecyclerView.Adapter<ShortcutsAdapter.ViewHolder> {
        private final List<Shortcut> data;

        private class ViewHolder extends RecyclerView.ViewHolder {
            private final ImageButton menuButton;
            private final ImageView imageView;
            private final TextView title;
            private final TextView subtitle;
            private final View innerArea;

            private ViewHolder(View view) {
                super(view);
                this.imageView = view.findViewById(R.id.ImageView);
                this.title = view.findViewById(R.id.TVTitle);
                this.subtitle = view.findViewById(R.id.TVSubtitle);
                this.menuButton = view.findViewById(R.id.BTMenu);
                this.innerArea = view.findViewById(R.id.LLInnerArea);
            }
        }

        public ShortcutsAdapter(List<Shortcut> data) {
            this.data = data;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.shortcut_list_item, parent, false));
        }

        @Override
        public void onViewRecycled(@NonNull ViewHolder holder) {
            holder.menuButton.setOnClickListener(null);
            holder.innerArea.setOnClickListener(null);
            holder.imageView.setOnLongClickListener(null);
            super.onViewRecycled(holder);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            final Shortcut item = data.get(position);
            
            // Display cover art if available, otherwise use icon
            if (item.getCoverArt() != null) {
                holder.imageView.setImageBitmap(item.getCoverArt());
            } else if (item.icon != null) {
                holder.imageView.setImageBitmap(item.icon);
            }
            
            holder.title.setText(item.name);
            holder.subtitle.setText(item.container.getName());
            holder.menuButton.setOnClickListener((v) -> showListItemMenu(v, item));
            holder.innerArea.setOnClickListener((v) -> runFromShortcut(item));
            
            // Add long press listener for cover art change
            holder.imageView.setOnLongClickListener((v) -> {
                showCoverArtSelectionDialog(item);
                return true;
            });
        }

        @Override
        public final int getItemCount() {
            return data.size();
        }

        private void showListItemMenu(View anchorView, final Shortcut shortcut) {
            final Context context = getContext();
            PopupMenu listItemMenu = new PopupMenu(context, anchorView);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) listItemMenu.setForceShowIcon(true);

            listItemMenu.inflate(R.menu.shortcut_popup_menu);
            listItemMenu.setOnMenuItemClickListener((menuItem) -> {
                int itemId = menuItem.getItemId();
                if (itemId == R.id.shortcut_settings) {
                    (new ShortcutSettingsDialog(ShortcutsFragment.this, shortcut)).show();
                }
                else if (itemId == R.id.shortcut_remove) {
                    ContentDialog.confirm(context, R.string.do_you_want_to_remove_this_shortcut, () -> {
                        boolean fileDeleted = shortcut.file.delete();
                        boolean iconFileDeleted = shortcut.iconFile != null && shortcut.iconFile.delete();

                        if (fileDeleted) {
                            disableShortcutOnScreen(requireContext(), shortcut);
                            loadShortcutsList();
                            Toast.makeText(context, "Shortcut removed successfully.", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(context, "Failed to remove the shortcut. Please try again.", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                else if (itemId == R.id.shortcut_clone_to_container) {
                    // Use the ContainerManager to get the list of containers
                    ContainerManager containerManager = new ContainerManager(context);
                    ArrayList<Container> containers = containerManager.getContainers();

                    // Show a container selection dialog
                    showContainerSelectionDialog(containers, new OnContainerSelectedListener() {
                        @Override
                        public void onContainerSelected(Container selectedContainer) {
                            // Use the selected container to clone the shortcut
                            if (shortcut.cloneToContainer(selectedContainer)) {
                                Toast.makeText(context, "Shortcut cloned successfully.", Toast.LENGTH_SHORT).show();
                                loadShortcutsList(); // Reload the shortcuts to show the cloned one
                            } else {
                                Toast.makeText(context, "Failed to clone shortcut.", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
                else if (itemId == R.id.shortcut_add_to_home_screen) {
                    if (shortcut.getExtra("uuid").equals(""))
                        shortcut.genUUID();
                    addShortcutToScreen(shortcut);
                }
                else if (itemId == R.id.shortcut_export_to_frontend) {
                    exportShortcutToFrontend(shortcut);
                }
                else if (itemId == R.id.shortcut_export_settings) {
                    exportShortcutSettingsAsync(shortcut);
                }
                else if (itemId == R.id.shortcut_import_settings) {
                    importShortcutSettings(shortcut);
                }
                else if (itemId == R.id.shortcut_properties) {
                    showShortcutProperties(shortcut);
                }
                return true;
            });
            listItemMenu.show();
        }


        // Define the listener interface for selecting a container
        public interface OnContainerSelectedListener {
            void onContainerSelected(Container container);
        }

        private void showContainerSelectionDialog(ArrayList<Container> containers, OnContainerSelectedListener listener) {
            // Create an AlertDialog to show the list of containers
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle("Select a container");

            // Create an array of container names to display
            String[] containerNames = new String[containers.size()];
            for (int i = 0; i < containers.size(); i++) {
                containerNames[i] = containers.get(i).getName();
            }

            // Set up the list in the dialog
            builder.setItems(containerNames, (dialog, which) -> {
                // Call the listener when a container is selected
                listener.onContainerSelected(containers.get(which));
            });

            // Show the dialog
            builder.show();
        }






        private void runFromShortcut(Shortcut shortcut) {
            Activity activity = getActivity();

            if (!XrActivity.isEnabled(getContext())) {
                Intent intent = new Intent(activity, XServerDisplayActivity.class);
                intent.putExtra("container_id", shortcut.container.id);
                intent.putExtra("shortcut_path", shortcut.file.getPath());
                intent.putExtra("shortcut_name", shortcut.name); // Add this line to pass the shortcut name
                // Check if the shortcut has the disableXinput value; if not, default to false.
                String disableXinputValue = shortcut.getExtra("disableXinput", "0"); // Get value from shortcut or use "0" (false) by default
                intent.putExtra("disableXinput", disableXinputValue); // Use the actual value from the shortcut
                activity.startActivity(intent);
            }
            else XrActivity.openIntent(activity, shortcut.container.id, shortcut.file.getPath());
        }

        private void exportShortcutToFrontend(Shortcut shortcut) {
            // Check for a custom frontend export path in shared preferences
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
            String uriString = sharedPreferences.getString("frontend_export_uri", null);

            File frontendDir;

            if (uriString != null) {
                // If custom URI is set, use it
                Uri folderUri = Uri.parse(uriString);
                DocumentFile pickedDir = DocumentFile.fromTreeUri(getContext(), folderUri);

                if (pickedDir == null || !pickedDir.canWrite()) {
                    Toast.makeText(getContext(), "Cannot write to the selected folder", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Convert DocumentFile to a File object for further processing
                frontendDir = new File(FileUtils.getFilePathFromUri(getContext(), folderUri));
            } else {
                // Default to Downloads\Winlator\Frontend if no custom URI is set
                frontendDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Winlator/Frontend");
                if (!frontendDir.exists() && !frontendDir.mkdirs()) {
                    Toast.makeText(getContext(), "Failed to create default directory", Toast.LENGTH_SHORT).show();
                    return;
                }
            }


            // Check for FRONTEND_INSTRUCTIONS.txt
            File instructionsFile = new File(frontendDir, "FRONTEND_INSTRUCTIONS.txt");
            if (!instructionsFile.exists()) {
                try (FileWriter writer = new FileWriter(instructionsFile, false)) {
                    writer.write("Instructions for adding Winlator shortcuts to Frontends (WIP):\n\n");
                    writer.write("Daijisho:\n\n");
                    writer.write("1. Open Daijisho\n");
                    writer.write("2. Navigate to the Settings tab.\n");
                    writer.write("3. Navigate to Settings\\Library\n");
                    writer.write("4. Select, Import from Pegasus\n");
                    writer.write("5. Add the metadata.pegasus.txt file located in this directory (Downloads\\Winlator\\Frontend)\n");
                    writer.write("6. Set the Sync path to Downloads\\Winlator\\Frontend\n");
                    writer.write("7. Start your game!\n\n");
                    writer.write("Beacon:\n\n");
                    writer.write("1. Navigate to Settings\n");
                    writer.write("2. Click the + Icon\n");
                    writer.write("3. Set the following values:\n\n");
                    writer.write("Platform Type: Custom\n");
                    writer.write("Name: Windows (or Winlator, whatever you prefer)\n");
                    writer.write("Short name: windows\n");
                    writer.write("Player app: Select Winlator.CMOD (or whichever fork you are using that has adopted this code)\n");
                    writer.write("ROMs folder: Use Android FilePicker to select the Downloads\\Winlator\\Frontend directory\n");
                    writer.write("Expand Advanced:\n");
                    writer.write("File handling: Default\n");
                    writer.write("Use custom launch: True\n");
                    writer.write("am start command: am start -n " + getContext().getPackageName() + "/com.winlator.xmod.XServerDisplayActivity -e shortcut_path {file_path}\n\n");
                    writer.write("4. Click Save\n");
                    writer.write("5. Scan the folder for your game\n");
                    writer.write("6. Launch your game!\n");
                    writer.flush();
                    Log.d("ShortcutsFragment", "FRONTEND_INSTRUCTIONS.txt created successfully.");
                } catch (IOException e) {
                    Log.e("ShortcutsFragment", "Failed to create FRONTEND_INSTRUCTIONS.txt", e);
                }
            }

            // Check for metadata.pegasus.txt
            File metadataFile = new File(frontendDir, "metadata.pegasus.txt");
            try (FileWriter writer = new FileWriter(metadataFile, false)) {
                writer.write("collection: Windows\n");
                writer.write("shortname: windows\n");
                writer.write("extensions: desktop\n");
                writer.write("launch: am start\n");
                writer.write("  -n " + getContext().getPackageName() + "/.XServerDisplayActivity\n");
                writer.write("  -e shortcut_path {file.path}\n");
                writer.write("  --activity-clear-task\n");
                writer.write("  --activity-clear-top\n");
                writer.write("  --activity-no-history\n");
                writer.flush();
                Log.d("ShortcutsFragment", "metadata.pegasus.txt created or updated successfully.");
            } catch (IOException e) {
                Log.e("ShortcutsFragment", "Failed to create or update metadata.pegasus.txt", e);
            }

            // Create the export file in the Frontend directory
            File exportFile = new File(frontendDir, shortcut.file.getName());

            boolean fileExists = exportFile.exists();
            boolean containerIdFound = false;

            try {
                List<String> lines = new ArrayList<>();

                // Read the original file or existing file if it exists
                try (BufferedReader reader = new BufferedReader(new FileReader(shortcut.file))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("container_id:")) {
                            // Replace the existing container_id line
                            lines.add("container_id:" + shortcut.container.id);
                            containerIdFound = true;
                        } else {
                            lines.add(line);
                        }
                    }
                }

                // If no container_id was found, add it
                if (!containerIdFound) {
                    lines.add("container_id:" + shortcut.container.id);
                }

                // Write the contents to the export file
                try (FileWriter writer = new FileWriter(exportFile, false)) {
                    for (String line : lines) {
                        writer.write(line + "\n");
                    }
                    writer.flush();
                }

                Log.d("ShortcutsFragment", "Shortcut exported successfully to " + exportFile.getPath());

                // Determine the toast message
                String message;
                if (fileExists) {
                    message = "Frontend Shortcut Updated at " + exportFile.getPath();
                } else {
                    message = "Frontend Shortcut Exported to " + exportFile.getPath();
                }

                // Show a toast message to the user
                Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();

            } catch (IOException e) {
                Log.e("ShortcutsFragment", "Failed to export shortcut", e);
                Toast.makeText(getContext(), "Failed to export shortcut", Toast.LENGTH_LONG).show();
            }
        }

        private void exportShortcutSettings(Shortcut shortcut) {
            ContentDialog dialog = new ContentDialog(getContext(), R.layout.export_settings_dialog);
            dialog.setTitle(getString(R.string.export_shortcut_settings));
            dialog.setIcon(R.drawable.icon_settings);
            CheckBox cbInclude = dialog.findViewById(R.id.CBIncludeContents);
            View btAdvanced = dialog.findViewById(R.id.BTAdvanced);
            View btAddInjection = dialog.findViewById(R.id.BTAddInjection);
            final com.winlator.xmod.core.ShortcutPackageManager.ExportOptions exportOptions = new com.winlator.xmod.core.ShortcutPackageManager.ExportOptions();
exportOptions.includeWine = false;

            if (btAdvanced != null) btAdvanced.setVisibility(cbInclude != null && cbInclude.isChecked() ? View.VISIBLE : View.GONE);
            if (cbInclude != null) {
                cbInclude.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (btAdvanced != null) btAdvanced.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                });
            }
            if (btAddInjection != null) {
                btAddInjection.setOnClickListener(v -> {
                    String[] items = new String[]{"Add file","Add folder"};
                    ContentDialog.showSingleChoiceList(getContext(), R.string.export_shortcut_settings, items, (which) -> {
                        if (which == 0) {
                            pendingExportOptions = exportOptions;
                            pendingInjectionIsDir = false;
                            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                            intent.addCategory(Intent.CATEGORY_OPENABLE);
                            intent.setType("*/*");
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivityForResult(Intent.createChooser(intent, "Select file to inject"), REQUEST_ADD_INJECTION_FILE);
                        } else if (which == 1) {
                            pendingExportOptions = exportOptions;
                            pendingInjectionIsDir = true;
                            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                            startActivityForResult(intent, REQUEST_ADD_INJECTION_FOLDER);
                        }
                    });
                });
            }
            if (btAdvanced != null) {
                btAdvanced.setOnClickListener(v -> {
                    ContentDialog adv = new ContentDialog(getContext(), R.layout.export_advanced_options_dialog);
                    adv.setTitle("Advanced export options");
                    adv.setOnConfirmCallback(() -> {
                        CheckBox w = adv.findViewById(R.id.CBAdvWine);
                        CheckBox dxvk = adv.findViewById(R.id.CBAdvDXVK);
                        CheckBox vkd3d = adv.findViewById(R.id.CBAdvVKD3D);
                        CheckBox box64 = adv.findViewById(R.id.CBAdvBox64);
                        CheckBox wow = adv.findViewById(R.id.CBAdvWOWBox64);
                        CheckBox fex = adv.findViewById(R.id.CBAdvFEXCore);
                        CheckBox adr = adv.findViewById(R.id.CBAdvAdrenotools);
                        if (w != null) { w.setChecked(false); w.setEnabled(false); exportOptions.includeWine = false; }
                        if (dxvk != null) exportOptions.includeDXVK = dxvk.isChecked();
                        if (vkd3d != null) exportOptions.includeVKD3D = vkd3d.isChecked();
                        if (box64 != null) exportOptions.includeBox64 = box64.isChecked();
                        if (wow != null) exportOptions.includeWOWBox64 = wow.isChecked();
                        if (fex != null) exportOptions.includeFEXCore = fex.isChecked();
                        if (adr != null) exportOptions.includeAdrenotools = adr.isChecked();
                    });
                    adv.show();
                });
            }

            dialog.setOnConfirmCallback(() -> {
                boolean includeContents = cbInclude != null && cbInclude.isChecked();
                File exportedFile = includeContents
                        ? com.winlator.xmod.core.ShortcutPackageManager.exportShortcutSettings(shortcut, getContext(), exportOptions)
                        : com.winlator.xmod.core.ShortcutConfigManager.exportShortcutSettings(shortcut, getContext());
                if (exportedFile != null) {
                    String msg = includeContents ? (getString(R.string.package_exported_to) + "\n" + exportedFile.getAbsolutePath())
                                                 : (getString(R.string.shortcut_settings_exported) + "\n" + exportedFile.getAbsolutePath());
                    Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(getContext(), getString(R.string.failed_to_export_shortcut_settings), Toast.LENGTH_SHORT).show();
                }
            });
            dialog.show();
        }

        private void exportShortcutSettingsAsync(Shortcut shortcut) {
            ContentDialog dialog = new ContentDialog(getContext(), R.layout.export_settings_dialog);
            dialog.setTitle(getString(R.string.export_shortcut_settings));
            dialog.setIcon(R.drawable.icon_settings);
            CheckBox cbInclude = dialog.findViewById(R.id.CBIncludeContents);
            View btAdvanced = dialog.findViewById(R.id.BTAdvanced);
            View btAddInjection = dialog.findViewById(R.id.BTAddInjection);
            final com.winlator.xmod.core.ShortcutPackageManager.ExportOptions exportOptions = new com.winlator.xmod.core.ShortcutPackageManager.ExportOptions();
exportOptions.includeWine = false;
            if (btAdvanced != null) btAdvanced.setVisibility(cbInclude != null && cbInclude.isChecked() ? View.VISIBLE : View.GONE);
            if (cbInclude != null) cbInclude.setOnCheckedChangeListener((b, checked) -> { if (btAdvanced != null) btAdvanced.setVisibility(checked ? View.VISIBLE : View.GONE); });
            if (btAddInjection != null) btAddInjection.setOnClickListener(v -> {
                String[] items = new String[]{"Add file","Add folder"};
                ContentDialog.showSingleChoiceList(getContext(), R.string.export_shortcut_settings, items, (which) -> {
                    if (which == 0) {
                        pendingExportOptions = exportOptions;
                        pendingInjectionIsDir = false;
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("*/*");
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivityForResult(Intent.createChooser(intent, "Select file to inject"), REQUEST_ADD_INJECTION_FILE);
                    } else if (which == 1) {
                        pendingExportOptions = exportOptions;
                        pendingInjectionIsDir = true;
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                        startActivityForResult(intent, REQUEST_ADD_INJECTION_FOLDER);
                    }
                });
            });
            if (btAdvanced != null) btAdvanced.setOnClickListener(v -> {
                ContentDialog adv = new ContentDialog(getContext(), R.layout.export_advanced_options_dialog);
                adv.setTitle("Advanced export options");
                adv.setOnConfirmCallback(() -> {
                    CheckBox w = adv.findViewById(R.id.CBAdvWine);
                    CheckBox dxvk = adv.findViewById(R.id.CBAdvDXVK);
                    CheckBox vkd3d = adv.findViewById(R.id.CBAdvVKD3D);
                    CheckBox box64 = adv.findViewById(R.id.CBAdvBox64);
                    CheckBox wow = adv.findViewById(R.id.CBAdvWOWBox64);
                    CheckBox fex = adv.findViewById(R.id.CBAdvFEXCore);
                    CheckBox adr = adv.findViewById(R.id.CBAdvAdrenotools);
                    if (w != null) { w.setChecked(false); w.setEnabled(false); exportOptions.includeWine = false; }
                    if (dxvk != null) exportOptions.includeDXVK = dxvk.isChecked();
                    if (vkd3d != null) exportOptions.includeVKD3D = vkd3d.isChecked();
                    if (box64 != null) exportOptions.includeBox64 = box64.isChecked();
                    if (wow != null) exportOptions.includeWOWBox64 = wow.isChecked();
                    if (fex != null) exportOptions.includeFEXCore = fex.isChecked();
                    if (adr != null) exportOptions.includeAdrenotools = adr.isChecked();
                });
                adv.show();
            });
            dialog.setOnConfirmCallback(() -> {
                boolean includeContents = cbInclude != null && cbInclude.isChecked();
                Activity activity = getActivity();
                if (activity == null) return;
                PreloaderDialog preloader = new PreloaderDialog(activity);
                preloader.showOnUiThread(R.string.exporting_settings);
                new Thread(() -> {
                    File exportedFile = includeContents
                            ? com.winlator.xmod.core.ShortcutPackageManager.exportShortcutSettings(shortcut, getContext(), exportOptions)
                            : com.winlator.xmod.core.ShortcutConfigManager.exportShortcutSettings(shortcut, getContext());
                    activity.runOnUiThread(() -> {
                        preloader.close();
                        if (exportedFile != null) {
                            String msg = includeContents
                                    ? (getString(R.string.package_exported_to) + "\n" + exportedFile.getAbsolutePath())
                                    : (getString(R.string.shortcut_settings_exported) + "\n" + exportedFile.getAbsolutePath());
                            Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(getContext(), getString(R.string.failed_to_export_shortcut_settings), Toast.LENGTH_SHORT).show();
                        }
                    });
                }).start();
            });
            dialog.show();
        }

        private void importShortcutSettings(Shortcut shortcut) {
            currentShortcutForImport = shortcut;
            openFilePicker();
        }

        private void showShortcutProperties(Shortcut shortcut) {
            SharedPreferences playtimePrefs = getContext().getSharedPreferences("playtime_stats", Context.MODE_PRIVATE);

            String playtimeKey = shortcut.name + "_playtime";
            String playCountKey = shortcut.name + "_play_count";

            long totalPlaytime = playtimePrefs.getLong(playtimeKey, 0);
            int playCount = playtimePrefs.getInt(playCountKey, 0);

            // Convert playtime to human-readable format
            long seconds = (totalPlaytime / 1000) % 60;
            long minutes = (totalPlaytime / (1000 * 60)) % 60;
            long hours = (totalPlaytime / (1000 * 60 * 60)) % 24;
            long days = (totalPlaytime / (1000 * 60 * 60 * 24));

            String playtimeFormatted = String.format("%dd %02dh %02dm %02ds", days, hours, minutes, seconds);

            // Create the properties dialog
            ContentDialog dialog = new ContentDialog(getContext(), R.layout.shortcut_properties_dialog);
            dialog.setTitle("Properties");

            TextView playCountTextView = dialog.findViewById(R.id.play_count);
            TextView playtimeTextView = dialog.findViewById(R.id.playtime);

            playCountTextView.setText("Number of times played: " + playCount);
            playtimeTextView.setText("Playtime: " + playtimeFormatted);

            Button resetPropertiesButton = dialog.findViewById(R.id.reset_properties);

            resetPropertiesButton.setOnClickListener(v -> {
                playtimePrefs.edit().remove(playtimeKey).remove(playCountKey).apply();
                Toast.makeText(getContext(), "Properties reset successfully.", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });

            dialog.show();
        }

        private void showCoverArtSelectionDialog(Shortcut shortcut) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle(getString(R.string.change_cover_art));
            
            String[] options = {
                getString(R.string.gallery),
                getString(R.string.camera),
                getString(R.string.remove_cover_art)
            };
            
            builder.setItems(options, (dialog, which) -> {
                currentShortcutForCover = shortcut;
                switch (which) {
                    case 0: // Gallery
                        openImageGallery();
                        break;
                    case 1: // Camera
                        openCamera();
                        break;
                    case 2: // Remove cover art
                        removeCoverArt(shortcut);
                        break;
                }
            });
            
            builder.setNegativeButton(getString(R.string.cancel), null);
            builder.show();
        }

        private void removeCoverArt(Shortcut shortcut) {
            ContentDialog.confirm(getContext(), 
                "Remove custom cover art for \"" + shortcut.name + "\"?",
                () -> {
                    shortcut.removeCustomCoverArt();
                    Toast.makeText(getContext(), getString(R.string.cover_art_removed), Toast.LENGTH_SHORT).show();
                    loadShortcutsList(); // Refresh the list to update the display
                });
        }


    }

    // Cover art change methods
    private void openImageGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_IMAGE_GALLERY);
    }

    private void openCamera() {
        // Check camera permission first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (requireContext().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                return;
            }
        }
        
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
            startActivityForResult(intent, REQUEST_IMAGE_CAMERA);
        } else {
            Toast.makeText(getContext(), "Camera not available", Toast.LENGTH_SHORT).show();
        }
    }

    // File picker for importing settings
    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        
        // Prefer .wsc files but allow all files in case user renamed extension
        String[] mimeTypes = {"application/json", "text/plain", "*/*"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        
        // Add extra to suggest file types
        intent.putExtra(Intent.EXTRA_TITLE, "Select Winlator Shortcut Config (.wsc)");
        
        try {
            startActivityForResult(Intent.createChooser(intent, getString(R.string.select_settings_file_to_import)), REQUEST_FILE_PICKER);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(getContext(), "Please install a File Manager app", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_FILE_PICKER && currentShortcutForImport != null && data != null) {
                // Handle file picker selection for import settings
                Uri fileUri = data.getData();
                if (fileUri != null) {
                    try {
                        // Validate off main thread, then import with progress
                        final Shortcut target = currentShortcutForImport;
                        currentShortcutForImport = null;
                        Activity activity = getActivity();
                        if (activity == null) return;
                        new Thread(() -> {
                            boolean valid = ShortcutPackageManager.isValid(fileUri, getContext());
                            String originalName = valid ? ShortcutPackageManager.getName(fileUri, getContext()) : null;
                            activity.runOnUiThread(() -> {
                                if (!valid) {
                                    Toast.makeText(getContext(), getString(R.string.invalid_settings_file), Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                ContentDialog.confirm(getContext(),
                                        "Import settings from \"" + originalName + "\"?\n\nThis will overwrite current settings for \"" + target.name + "\".",
                                        () -> {
                                            PreloaderDialog preloader = new PreloaderDialog(activity);
                                            preloader.showOnUiThread(R.string.importing_settings);
                                            new Thread(() -> {
                                                boolean success = ShortcutPackageManager.importFromUri(target, fileUri, getContext());
                                                activity.runOnUiThread(() -> {
                                                    preloader.close();
                                                    if (success) {
                                                        Toast.makeText(getContext(), getString(R.string.shortcut_settings_imported), Toast.LENGTH_SHORT).show();
                                                        loadShortcutsList();
                                                    } else {
                                                        Toast.makeText(getContext(), getString(R.string.failed_to_import_shortcut_settings), Toast.LENGTH_SHORT).show();
                                                    }
                                                });
                                            }).start();
                                        });
                            });
                        }).start();
                    } catch (Exception e) {
                        Log.e("ShortcutsFragment", "Error processing selected config file", e);
                        Toast.makeText(getContext(), getString(R.string.failed_to_import_shortcut_settings) + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
                    } finally {
                        currentShortcutForImport = null;
                    }
                } else {
                    Log.e("ShortcutsFragment", "No file URI received from picker");
                    Toast.makeText(getContext(), "No file selected", Toast.LENGTH_SHORT).show();
                    currentShortcutForImport = null;
                }
            } else if (requestCode == REQUEST_NEW_SHORTCUT_COVER) {
                try {
                    Bitmap selectedBitmap = null;
                    if (data != null) {
                        Uri imageUri = data.getData();
                        if (imageUri != null) {
                            selectedBitmap = MediaStore.Images.Media.getBitmap(requireContext().getContentResolver(), imageUri);
                        }
                    }
                    if (selectedBitmap != null) {
                        pendingCoverBitmap = resizeBitmap(selectedBitmap, 512, 512);
                        if (pendingCoverPreview != null) pendingCoverPreview.setImageBitmap(pendingCoverBitmap);
                    } else {
                        Toast.makeText(getContext(), getString(R.string.failed_to_update_cover_art), Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Log.e("ShortcutsFragment", "Error processing selected image for new shortcut", e);
                }
            } else if (requestCode == REQUEST_ADD_INJECTION_FILE && data != null && pendingExportOptions != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    String path = com.winlator.xmod.core.FileUtils.getFilePathFromUri(getContext(), uri);
                    if (path != null && !path.isEmpty()) {
                        ContentDialog.prompt(getContext(), R.string.enter_target_path, "C:\\", (target) -> {
                            pendingExportOptions.addInjection(path, target, false);
                            Toast.makeText(getContext(), "Injection added", Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            } else if (requestCode == REQUEST_ADD_INJECTION_FOLDER && data != null && pendingExportOptions != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    String path = com.winlator.xmod.core.FileUtils.getFilePathFromUriUsingSAF(getContext(), uri);
                    if (path != null && !path.isEmpty()) {
                        ContentDialog.prompt(getContext(), R.string.enter_target_path, "C:\\", (target) -> {
                            pendingExportOptions.addInjection(path, target, true);
                            Toast.makeText(getContext(), "Injection added", Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            } else if (currentShortcutForCover != null) {
                // Handle cover art selection for existing shortcut
                try {
                    Bitmap selectedBitmap = null;
                    
                    if (requestCode == REQUEST_IMAGE_GALLERY && data != null) {
                        Uri imageUri = data.getData();
                        if (imageUri != null) {
                            selectedBitmap = MediaStore.Images.Media.getBitmap(requireContext().getContentResolver(), imageUri);
                        }
                    } else if (requestCode == REQUEST_IMAGE_CAMERA && data != null) {
                        Bundle extras = data.getExtras();
                        if (extras != null) {
                            selectedBitmap = (Bitmap) extras.get("data");
                        }
                    }
                    
                    if (selectedBitmap != null) {
                        selectedBitmap = resizeBitmap(selectedBitmap, 512, 512);
                        currentShortcutForCover.saveCustomCoverArt(selectedBitmap);
                        Toast.makeText(getContext(), getString(R.string.cover_art_updated), Toast.LENGTH_SHORT).show();
                        loadShortcutsList();
                    } else {
                        Toast.makeText(getContext(), getString(R.string.failed_to_update_cover_art), Toast.LENGTH_SHORT).show();
                    }
                    
                } catch (Exception e) {
                    Log.e("ShortcutsFragment", "Error processing selected image", e);
                    Toast.makeText(getContext(), getString(R.string.failed_to_update_cover_art), Toast.LENGTH_SHORT).show();
                } finally {
                    currentShortcutForCover = null;
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, open camera
                openCamera();
            } else {
                Toast.makeText(getContext(), "Camera permission is required to take photos", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private Bitmap resizeBitmap(Bitmap bitmap, int maxWidth, int maxHeight) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        
        if (width <= maxWidth && height <= maxHeight) {
            return bitmap;
        }
        
        float aspectRatio = (float) width / height;
        
        if (width > height) {
            width = maxWidth;
            height = Math.round(width / aspectRatio);
        } else {
            height = maxHeight;
            width = Math.round(height * aspectRatio);
        }
        
        return Bitmap.createScaledBitmap(bitmap, width, height, true);
    }

    private ShortcutInfo buildScreenShortCut(String shortLabel, String longLabel, int containerId, String shortcutPath, Icon icon, String uuid) {
        Intent intent = new Intent(getActivity(), XServerDisplayActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra("container_id", containerId);
        intent.putExtra("shortcut_path", shortcutPath);

        return new ShortcutInfo.Builder(getActivity(), uuid)
                .setShortLabel(shortLabel)
                .setLongLabel(longLabel)
                .setIcon(icon)
                .setIntent(intent)
                .build();
    }

    private void addShortcutToScreen(Shortcut shortcut) {
        ShortcutManager shortcutManager = getSystemService(requireContext(), ShortcutManager.class);
        if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported())
            shortcutManager.requestPinShortcut(buildScreenShortCut(shortcut.name, shortcut.name, shortcut.container.id,
                    shortcut.file.getPath(), Icon.createWithBitmap(shortcut.icon), shortcut.getExtra("uuid")), null);
    }

    public static void disableShortcutOnScreen(Context context, Shortcut shortcut) {
        ShortcutManager shortcutManager = getSystemService(context, ShortcutManager.class);
        try {
            shortcutManager.disableShortcuts(Collections.singletonList(shortcut.getExtra("uuid")),
                    context.getString(R.string.shortcut_not_available));
        } catch (Exception e) {}
    }

    public void updateShortcutOnScreen(String shortLabel, String longLabel, int containerId, String shortcutPath, Icon icon, String uuid) {
        ShortcutManager shortcutManager = getSystemService(requireContext(), ShortcutManager.class);
        try {
            for (ShortcutInfo shortcutInfo : shortcutManager.getPinnedShortcuts()) {
                if (shortcutInfo.getId().equals(uuid)) {
                    shortcutManager.updateShortcuts(Collections.singletonList(
                            buildScreenShortCut(shortLabel, longLabel, containerId, shortcutPath, icon, uuid)));
                    break;
                }
            }
        } catch (Exception e) {}
    }

    // Custom shortcut creation methods
    private void showContainerSelectionForNewShortcut() {
        ArrayList<Container> containers = manager.getContainers();
        if (containers.isEmpty()) {
            Toast.makeText(getContext(), "No containers available. Please create a container first.", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(getString(R.string.select_shortcut_container));

        // Create container names array
        String[] containerNames = new String[containers.size()];
        for (int i = 0; i < containers.size(); i++) {
            containerNames[i] = containers.get(i).getName() + " (ID: " + containers.get(i).id + ")";
        }

        builder.setItems(containerNames, (dialog, which) -> {
            selectedContainer = containers.get(which);
            showExecutableSelection();
        });

        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.show();
    }

    private void showExecutableSelection() {
        FilePickerDialog filePickerDialog = new FilePickerDialog(getContext(), new FilePickerDialog.OnFileSelectedListener() {
            @Override
            public void onFileSelected(File file) {
                try {
                    String filePath = file.getAbsolutePath();
                    String fileName = getExecutableNameFromPath(filePath);
                    
                    Log.d("ShortcutsFragment", "Selected file via custom picker: " + filePath);
                    showShortcutNameDialog(filePath, fileName);
                    
                } catch (Exception e) {
                    Log.e("ShortcutsFragment", "Error processing selected file", e);
                    Toast.makeText(getContext(), getString(R.string.failed_to_create_shortcut), Toast.LENGTH_SHORT).show();
                }
            }
        });
        
        filePickerDialog.show();
    }

    private void showShortcutNameDialog(String executablePath, String defaultName) {
        ContentDialog dialog = new ContentDialog(getContext(), R.layout.shortcut_create_dialog);
        dialog.setTitle(getString(R.string.shortcut_name));

        final EditText input = dialog.findViewById(R.id.ETShortcutName);
        final ImageView coverPreview = dialog.findViewById(R.id.IVCoverPreview);
        final View btChooseCover = dialog.findViewById(R.id.BTChooseCover);
        final View btRemoveCover = dialog.findViewById(R.id.BTRemoveCover);

        if (input != null) {
            input.setText(defaultName);
            input.setSelectAllOnFocus(true);
        }
        if (pendingCoverBitmap != null && coverPreview != null) coverPreview.setImageBitmap(pendingCoverBitmap);

        if (btChooseCover != null) btChooseCover.setOnClickListener(v -> {
            pendingCoverPreview = coverPreview;
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            startActivityForResult(intent, REQUEST_NEW_SHORTCUT_COVER);
        });
        if (btRemoveCover != null) btRemoveCover.setOnClickListener(v -> {
            pendingCoverBitmap = null;
            if (coverPreview != null) coverPreview.setImageResource(R.drawable.wallpaper);
        });

        dialog.setOnConfirmCallback(() -> {
            String shortcutName = input != null ? input.getText().toString().trim() : defaultName;
            if (shortcutName.isEmpty()) shortcutName = defaultName;
            createCustomShortcut(shortcutName, executablePath);
        });
        dialog.show();
    }

    private void createCustomShortcut(String shortcutName, String executablePath) {
        if (selectedContainer == null) {
            Toast.makeText(getContext(), "No container selected", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Create the desktop file
            File desktopDir = selectedContainer.getDesktopDir();
            if (!desktopDir.exists()) {
                desktopDir.mkdirs();
            }

            File shortcutFile = new File(desktopDir, shortcutName + ".desktop");

            // Convert file path to wine path format (simple Z: mapping)
            String winePath = convertToWinePath(executablePath);
            
            Log.d("ShortcutsFragment", "Creating shortcut for executable: " + executablePath);
            Log.d("ShortcutsFragment", "Wine path: " + winePath);

            // Create the desktop file content - simple format that Winlator can process
            StringBuilder content = new StringBuilder();
            content.append("[Desktop Entry]\n");
            content.append("Name=").append(shortcutName).append("\n");
            content.append("Exec=").append(winePath).append("\n");
            content.append("Type=Application\n");
            content.append("StartupNotify=true\n");
            
            // Add Path field pointing to the directory
            String exeDir = FileUtils.getDirname(winePath);
            if (exeDir != null && !exeDir.isEmpty()) {
                content.append("Path=").append(exeDir).append("\n");
            }
            
            content.append("Icon=").append(shortcutName.toLowerCase()).append(".0\n");
            content.append("StartupWMClass=").append(shortcutName.toLowerCase()).append(".exe\n");
            
            content.append("\n[Extra Data]\n");
            content.append("container_id=").append(selectedContainer.id).append("\n");
            content.append("uuid=\n"); // Will be generated when needed

            Log.d("ShortcutsFragment", "Desktop file content:\n" + content.toString());

            // Write the file
            FileUtils.writeString(shortcutFile, content.toString());

            // Apply pending cover art if selected
            if (pendingCoverBitmap != null) {
                try {
                    com.winlator.xmod.container.Shortcut newShortcut = new com.winlator.xmod.container.Shortcut(selectedContainer, shortcutFile);
                    android.graphics.Bitmap resized = resizeBitmap(pendingCoverBitmap, 512, 512);
                    newShortcut.saveCustomCoverArt(resized);
                } catch (Exception ignore) {}
                pendingCoverBitmap = null;
                pendingCoverPreview = null;
            }

            Toast.makeText(getContext(), getString(R.string.shortcut_created_successfully), Toast.LENGTH_SHORT).show();
            loadShortcutsList();

        } catch (Exception e) {
            Log.e("ShortcutsFragment", "Failed to create custom shortcut", e);
            Toast.makeText(getContext(), getString(R.string.failed_to_create_shortcut), Toast.LENGTH_SHORT).show();
        }
    }

    private String convertToWinePath(String androidPath) {
        if (androidPath == null || androidPath.isEmpty()) return "";

        String bestLetter = null;
        String bestBase = null;
        int bestLen = -1;

        if (selectedContainer != null) {
            for (String[] drive : selectedContainer.drivesIterator()) {
                String letter = drive[0];
                String base = drive[1];
                if (base == null || base.isEmpty()) continue;
                String normBase = base.endsWith("/") ? base : base + "/";
                if (androidPath.startsWith(normBase)) {
                    if (normBase.length() > bestLen) {
                        bestLen = normBase.length();
                        bestLetter = letter;
                        bestBase = normBase;
                    }
                } else if (androidPath.equals(base)) {
                    if (base.length() > bestLen) {
                        bestLen = base.length();
                        bestLetter = letter;
                        bestBase = base;
                    }
                }
            }
        }

        String winePath;
        if (bestLetter != null) {
            String remainder = androidPath.substring(Math.min(bestBase.length(), androidPath.length()));
            if (!remainder.startsWith("/")) remainder = "/" + remainder;
            winePath = bestLetter + ":" + remainder.replace("/", "\\");
        } else {
            String normalized = androidPath;
            if (normalized.startsWith("/sdcard/")) {
                normalized = normalized.replace("/sdcard", "/storage/emulated/0");
            }
            winePath = "Z:" + normalized.replace("/", "\\");
        }

        if (winePath.contains(" ")) {
            winePath = "\"" + winePath + "\"";
        }

        Log.d("ShortcutsFragment", "Path conversion (drives-aware): " + androidPath + " -> " + winePath);
        return winePath;
    }

    private String getExecutableNameFromPath(String path) {
        if (path == null) return "Game";
        
        int lastSlash = path.lastIndexOf('/');
        String fileName = lastSlash != -1 ? path.substring(lastSlash + 1) : path;
        
        // Remove extension
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot != -1) {
            fileName = fileName.substring(0, lastDot);
        }
        
        return fileName.isEmpty() ? "Game" : fileName;
    }

    // Improved URI to file path resolution - ALWAYS preserve original file location
    private String getFilePathFromUri(Uri uri) {
        if (uri == null) return null;
        
        String scheme = uri.getScheme();
        Log.d("ShortcutsFragment", "Resolving URI: " + uri + " (scheme: " + scheme + ")");
        
        try {
            // Handle different URI schemes
            if ("file".equalsIgnoreCase(scheme)) {
                // Direct file URI - easiest case
                String path = uri.getPath();
                Log.d("ShortcutsFragment", "File scheme path: " + path);
                return path;
            } else if ("content".equalsIgnoreCase(scheme)) {
                // Content provider URI - need to resolve to actual path
                return getPathFromContentUri(uri);
            }
        } catch (Exception e) {
            Log.e("ShortcutsFragment", "Error resolving URI path: " + uri, e);
        }
        
        return null;
    }

    private String getPathFromContentUri(Uri uri) {
        try {
            // Handle document provider URIs (Android 4.4+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && 
                DocumentsContract.isDocumentUri(getContext(), uri)) {
                return getPathFromDocumentUri(uri);
            }
            
            // Handle MediaStore URIs (older Android versions or MediaStore content)
            return getPathFromMediaStore(uri);
            
        } catch (Exception e) {
            Log.e("ShortcutsFragment", "Error getting path from content URI: " + uri, e);
        }
        
        return null;
    }

    private String getPathFromDocumentUri(Uri uri) {
        try {
            String documentId = DocumentsContract.getDocumentId(uri);
            Log.d("ShortcutsFragment", "Document ID: " + documentId);
            
            // Parse document ID
            String[] split = documentId.split(":");
            if (split.length < 2) {
                Log.w("ShortcutsFragment", "Invalid document ID format: " + documentId);
                return null;
            }
            
            String type = split[0];
            String path = split[1];
            
            Log.d("ShortcutsFragment", "Document type: " + type + ", path: " + path);

            if ("primary".equalsIgnoreCase(type)) {
                // Internal storage
                String fullPath = Environment.getExternalStorageDirectory() + "/" + path;
                Log.d("ShortcutsFragment", "Primary storage path: " + fullPath);
                return fullPath;
            } else {
                // External storage (SD card, USB, etc.)
                // Try multiple possible mount points
                String[] possibleRoots = {
                    "/storage/" + type,
                    "/mnt/media_rw/" + type,
                    "/storage/emulated/" + type,
                    "/mnt/" + type
                };
                
                for (String root : possibleRoots) {
                    String fullPath = root + "/" + path;
                    File testFile = new File(fullPath);
                    Log.d("ShortcutsFragment", "Testing path: " + fullPath + " (exists: " + testFile.exists() + ")");
                    
                    if (testFile.exists()) {
                        Log.d("ShortcutsFragment", "Found valid path: " + fullPath);
                        return fullPath;
                    }
                }
                
                // If no path worked, return the most likely one for logging
                String fallbackPath = "/storage/" + type + "/" + path;
                Log.w("ShortcutsFragment", "No valid path found, returning fallback: " + fallbackPath);
                return fallbackPath;
            }
        } catch (Exception e) {
            Log.e("ShortcutsFragment", "Error getting path from document URI: " + uri, e);
        }
        
        return null;
    }

    private String getPathFromMediaStore(Uri uri) {
        try {
            String[] projection = {MediaStore.MediaColumns.DATA};
            Cursor cursor = getContext().getContentResolver().query(uri, projection, null, null, null);
            
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
                        String path = cursor.getString(columnIndex);
                        Log.d("ShortcutsFragment", "MediaStore path: " + path);
                        return path;
                    }
                } finally {
                    cursor.close();
                }
            }
        } catch (Exception e) {
            Log.e("ShortcutsFragment", "Error getting path from MediaStore: " + uri, e);
        }
        
        return null;
    }

    // Extract executable name from URI without copying file
    private String getExecutableNameFromUri(Uri uri) {
        try {
            // Try to get display name from content resolver
            String[] projection = {OpenableColumns.DISPLAY_NAME};
            Cursor cursor = getContext().getContentResolver().query(uri, projection, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (nameIndex != -1) {
                            String displayName = cursor.getString(nameIndex);
                            if (displayName != null && !displayName.isEmpty()) {
                                // Remove extension
                                int lastDot = displayName.lastIndexOf('.');
                                if (lastDot != -1) {
                                    return displayName.substring(0, lastDot);
                                }
                                return displayName;
                            }
                        }
                    }
                } finally {
                    cursor.close();
                }
            }
            
            // Fallback: try to extract from URI path
            String path = uri.getPath();
            if (path != null) {
                return getExecutableNameFromPath(path);
            }
        } catch (Exception e) {
            Log.e("ShortcutsFragment", "Error getting executable name from URI", e);
        }
        
        return "Game"; // Default fallback name
    }
}
