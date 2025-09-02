package com.winlator.cmod.contentdialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.winlator.cmod.R;

import java.io.File;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FilePickerDialog extends Dialog {
    private static final String TAG = "FilePickerDialog";
    
    private TextView currentPathTextView;
    private RecyclerView filesRecyclerView;
    private Button upButton, internalButton, externalButton, cancelButton, selectButton;
    
    private File currentDirectory;
    private FileAdapter adapter;
    private File selectedFile;
    private OnFileSelectedListener listener;
    
    public interface OnFileSelectedListener {
        void onFileSelected(File file);
    }
    
    public FilePickerDialog(Context context, OnFileSelectedListener listener) {
        super(context, R.style.ContentDialog);
        this.listener = listener;
        initializeDialog();
    }
    
    private void initializeDialog() {
        setContentView(R.layout.file_picker_dialog);
        setTitle("Select Executable");
        
        currentPathTextView = findViewById(R.id.tv_current_path);
        filesRecyclerView = findViewById(R.id.recycler_files);
        upButton = findViewById(R.id.btn_up);
        internalButton = findViewById(R.id.btn_internal);
        externalButton = findViewById(R.id.btn_external);
        cancelButton = findViewById(R.id.btn_cancel);
        selectButton = findViewById(R.id.btn_select);
        
        setupRecyclerView();
        setupButtons();
        
        // Start with internal storage
        navigateToInternalStorage();
    }
    
    private void setupRecyclerView() {
        adapter = new FileAdapter();
        filesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        filesRecyclerView.setAdapter(adapter);
    }
    
    private void setupButtons() {
        upButton.setOnClickListener(v -> navigateUp());
        internalButton.setOnClickListener(v -> navigateToInternalStorage());
        externalButton.setOnClickListener(v -> navigateToExternalStorage());
        cancelButton.setOnClickListener(v -> dismiss());
        selectButton.setOnClickListener(v -> {
            if (selectedFile != null && listener != null) {
                listener.onFileSelected(selectedFile);
                dismiss();
            }
        });
    }
    
    private void navigateToInternalStorage() {
        File internalStorage = Environment.getExternalStorageDirectory();
        navigateToDirectory(internalStorage);
    }
    
    private void navigateToExternalStorage() {
        // Enhanced SD card detection
        List<File> externalDirs = getExternalStorageDirectories();
        
        if (!externalDirs.isEmpty()) {
            // Navigate to the first available external storage
            navigateToDirectory(externalDirs.get(0));
        } else {
            Toast.makeText(getContext(), "No external storage found", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Enhanced method to find external storage directories including SD cards
     */
    private List<File> getExternalStorageDirectories() {
        List<File> externalDirs = new ArrayList<>();
        Context context = getContext();
        
        try {
            // Method 1: Use StorageManager (Android 6.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                StorageManager storageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
                if (storageManager != null) {
                    try {
                        Method getVolumeListMethod = StorageManager.class.getMethod("getVolumeList");
                        Object[] volumes = (Object[]) getVolumeListMethod.invoke(storageManager);
                        
                        if (volumes != null) {
                            for (Object volume : volumes) {
                                Method isRemovableMethod = volume.getClass().getMethod("isRemovable");
                                Method getStateMethod = volume.getClass().getMethod("getState");
                                Method getPathMethod = volume.getClass().getMethod("getPath");
                                
                                boolean isRemovable = (Boolean) isRemovableMethod.invoke(volume);
                                String state = (String) getStateMethod.invoke(volume);
                                String path = (String) getPathMethod.invoke(volume);
                                
                                if (isRemovable && "mounted".equals(state) && path != null) {
                                    File dir = new File(path);
                                    if (dir.exists() && dir.canRead()) {
                                        externalDirs.add(dir);
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.d(TAG, "StorageManager method failed, trying alternative methods", e);
                    }
                }
            }
            
            // Method 2: Check common SD card paths
            String[] commonPaths = {
                "/storage/sdcard1",
                "/storage/extsd", 
                "/storage/external_sd",
                "/mnt/extsd",
                "/mnt/external_sd",
                "/storage/removable",
                "/storage/sdcard0/external_sd"
            };
            
            for (String path : commonPaths) {
                File dir = new File(path);
                if (dir.exists() && dir.canRead() && !externalDirs.contains(dir)) {
                    externalDirs.add(dir);
                }
            }
            
            // Method 3: Scan /storage directory
            File storageDir = new File("/storage");
            if (storageDir.exists() && storageDir.canRead()) {
                File[] children = storageDir.listFiles();
                if (children != null) {
                    for (File child : children) {
                        if (child.isDirectory() && 
                            !child.getName().equals("emulated") && 
                            !child.getName().equals("self") &&
                            child.canRead() && 
                            !externalDirs.contains(child)) {
                            
                            // Additional check to see if this is actually external storage
                            if (isExternalStorage(child)) {
                                externalDirs.add(child);
                            }
                        }
                    }
                }
            }
            
            // Method 4: Use getExternalFilesDirs for app-specific external storage
            File[] appExternalDirs = context.getExternalFilesDirs(null);
            if (appExternalDirs != null && appExternalDirs.length > 1) {
                for (int i = 1; i < appExternalDirs.length; i++) {
                    if (appExternalDirs[i] != null) {
                        // Navigate up to the root of the external storage
                        File root = getStorageRoot(appExternalDirs[i]);
                        if (root != null && !externalDirs.contains(root)) {
                            externalDirs.add(root);
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error detecting external storage", e);
        }
        
        return externalDirs;
    }
    
    /**
     * Check if a directory is external storage by looking for typical characteristics
     */
    private boolean isExternalStorage(File dir) {
        try {
            // Check if directory has typical external storage folders
            String[] typicalFolders = {"DCIM", "Download", "Pictures", "Music", "Movies"};
            File[] children = dir.listFiles();
            
            if (children != null) {
                int foundFolders = 0;
                for (File child : children) {
                    if (child.isDirectory()) {
                        for (String folder : typicalFolders) {
                            if (child.getName().equalsIgnoreCase(folder)) {
                                foundFolders++;
                                break;
                            }
                        }
                    }
                }
                // If we find at least 2 typical folders, it's likely external storage
                return foundFolders >= 2;
            }
        } catch (SecurityException e) {
            Log.d(TAG, "Security exception checking external storage: " + dir.getPath());
        }
        
        return false;
    }
    
    /**
     * Navigate up the directory tree to find the storage root
     */
    private File getStorageRoot(File file) {
        File parent = file;
        while (parent != null && parent.getParent() != null) {
            File grandParent = parent.getParentFile();
            if (grandParent != null && (grandParent.getName().equals("storage") || grandParent.getAbsolutePath().equals("/"))) {
                return parent;
            }
            parent = grandParent;
        }
        return parent;
    }
    
    private void navigateUp() {
        if (currentDirectory != null) {
            File parent = currentDirectory.getParentFile();
            if (parent != null && parent.canRead()) {
                navigateToDirectory(parent);
            }
        }
    }
    
    private void navigateToDirectory(File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            Toast.makeText(getContext(), "Cannot access directory", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Check read permission
        if (!directory.canRead()) {
            Toast.makeText(getContext(), "Permission denied to access this directory", Toast.LENGTH_SHORT).show();
            return;
        }
        
        currentDirectory = directory;
        currentPathTextView.setText(directory.getPath());
        loadDirectoryContents();
        
        // Update up button state
        upButton.setEnabled(directory.getParentFile() != null && directory.getParentFile().canRead());
    }
    
    private void loadDirectoryContents() {
        try {
            File[] files = currentDirectory.listFiles();
            List<FileItem> items = new ArrayList<>();
            
            if (files != null) {
                Arrays.sort(files, (f1, f2) -> {
                    // Directories first, then files
                    if (f1.isDirectory() && !f2.isDirectory()) return -1;
                    if (!f1.isDirectory() && f2.isDirectory()) return 1;
                    return f1.getName().compareToIgnoreCase(f2.getName());
                });
                
                for (File file : files) {
                    if (file.canRead()) {
                        if (file.isDirectory()) {
                            items.add(new FileItem(file, FileItem.TYPE_DIRECTORY));
                        } else if (isExecutableFile(file)) {
                            items.add(new FileItem(file, FileItem.TYPE_EXECUTABLE));
                        }
                    }
                }
            }
            
            adapter.setItems(items);
            
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception loading directory contents", e);
            Toast.makeText(getContext(), "Permission denied to read this directory", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error loading directory contents", e);
            Toast.makeText(getContext(), "Error reading directory", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Enhanced method to check if file is executable
     */
    private boolean isExecutableFile(File file) {
        if (file.isDirectory()) return false;
        
        String name = file.getName().toLowerCase();
        
        // Check for various executable extensions
        String[] executableExtensions = {
            ".exe", ".bat", ".cmd", ".com", ".scr", ".pif",
            ".msi", ".jar", ".app", ".deb", ".rpm", ".dmg"
        };
        
        for (String ext : executableExtensions) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        
        return false;
    }
    
    private void selectFile(File file) {
        if (file.isDirectory()) {
            navigateToDirectory(file);
        } else if (isExecutableFile(file)) {
            selectedFile = file;
            selectButton.setEnabled(true);
            // Update UI to show selection
            adapter.setSelectedFile(file);
        }
    }
    
    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {
        private List<FileItem> items = new ArrayList<>();
        private File selectedFile;
        
        public void setItems(List<FileItem> items) {
            this.items = items;
            notifyDataSetChanged();
        }
        
        public void setSelectedFile(File file) {
            this.selectedFile = file;
            notifyDataSetChanged();
        }
        
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.file_picker_item, parent, false);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FileItem item = items.get(position);
            holder.bind(item);
        }
        
        @Override
        public int getItemCount() {
            return items.size();
        }
        
        class ViewHolder extends RecyclerView.ViewHolder {
            private ImageView iconView, selectionIndicator;
            private TextView nameView, detailsView;
            
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                iconView = itemView.findViewById(R.id.iv_file_icon);
                nameView = itemView.findViewById(R.id.tv_file_name);
                detailsView = itemView.findViewById(R.id.tv_file_details);
                selectionIndicator = itemView.findViewById(R.id.iv_selection_indicator);
                
                itemView.setOnClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        selectFile(items.get(position).file);
                    }
                });
            }
            
            public void bind(FileItem item) {
                nameView.setText(item.file.getName());
                
                // Set icon based on type
                if (item.type == FileItem.TYPE_DIRECTORY) {
                    iconView.setImageResource(R.drawable.icon_open);
                } else {
                    iconView.setImageResource(R.drawable.icon_wine);
                }
                
                // Set details
                if (item.type == FileItem.TYPE_DIRECTORY) {
                    try {
                        File[] children = item.file.listFiles();
                        int childCount = children != null ? children.length : 0;
                        detailsView.setText(childCount + " items");
                    } catch (SecurityException e) {
                        detailsView.setText("Directory");
                    }
                } else {
                    long size = item.file.length();
                    String sizeStr = formatFileSize(size);
                    String date = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                        .format(new Date(item.file.lastModified()));
                    detailsView.setText(sizeStr + " • " + date);
                }
                
                // Show/hide selection indicator
                if (item.file.equals(selectedFile)) {
                    selectionIndicator.setVisibility(View.VISIBLE);
                    itemView.setSelected(true);
                } else {
                    selectionIndicator.setVisibility(View.GONE);
                    itemView.setSelected(false);
                }
            }
        }
    }
    
    private static class FileItem {
        static final int TYPE_DIRECTORY = 0;
        static final int TYPE_EXECUTABLE = 1;
        
        final File file;
        final int type;
        
        FileItem(File file, int type) {
            this.file = file;
            this.type = type;
        }
    }
    
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(Locale.getDefault(), "%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}