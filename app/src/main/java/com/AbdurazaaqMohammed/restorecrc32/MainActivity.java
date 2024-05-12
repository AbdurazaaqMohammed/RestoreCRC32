package com.AbdurazaaqMohammed.restorecrc32;


import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.github.angads25.filepicker.model.DialogConfigs;
import com.github.angads25.filepicker.model.DialogProperties;
import com.github.angads25.filepicker.view.FilePickerDialog;

import net.lingala.zip4j.io.inputstream.ZipInputStream;
import net.lingala.zip4j.model.LocalFileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionMethod;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipException;

public class MainActivity extends Activity {
    private static final int REQUEST_CODE_SET_ORIGINAL_FILE = 1;
    private static final int REQUEST_CODE_SET_FILE_TO_PATCH = 2;
    private static final int REQUEST_CODE_SAVE_FILE = 3;
    private static Uri uriOfOriginalFile;
    private static Uri uriOfFileToBePatched;
    private final static boolean supportsInbuiltAndroidFilePicker = Build.VERSION.SDK_INT > 18;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            getActionBar().hide();
        }
        setContentView(R.layout.activity_main);

        // Load settings from SharedPreferences
        SharedPreferences settings = getSharedPreferences("set", Context.MODE_PRIVATE);
        PatchFileAsyncTask.onlyPatchAndroidManifestAndClasses = settings.getBoolean("onlyPatchAMAndClasses", true);
        PatchFileAsyncTask.restoreLastModifiedDate = settings.getBoolean("restoreLastModifiedDate", false);

        // Check if user shared files with the app
        final Intent fromShareOrView = getIntent();
        final String action = fromShareOrView.getAction();
        if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            if (fromShareOrView.hasExtra(Intent.EXTRA_STREAM)) {
                ArrayList<Uri> inputUris = fromShareOrView.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                showSharedFilesDialog(inputUris, true);
            } else setupSwitches();
        } else if (Intent.ACTION_SEND.equals(action)) {
            if (fromShareOrView.hasExtra(Intent.EXTRA_STREAM)) {
                showSingleFileDialog(fromShareOrView.getParcelableExtra(Intent.EXTRA_STREAM));
            }
            setupSwitches();
        } else {
            setupSwitches();
        }
        // If multiple files were shared with the app, no need to initialize switches as everything is done via the dialogs.
    }

    private void setupSwitches() {
        findViewById(R.id.pickOriginalFile).setOnClickListener(view -> openFilePickerToSelectFile(REQUEST_CODE_SET_ORIGINAL_FILE));
        findViewById(R.id.pickModifiedFile).setOnClickListener(view -> openFilePickerToSelectFile(REQUEST_CODE_SET_FILE_TO_PATCH));
        findViewById(R.id.start).setOnClickListener(view -> {
            if(supportsInbuiltAndroidFilePicker) {
                if(uriOfOriginalFile == null) showError(getString(R.string.err_orig));
                else if (uriOfFileToBePatched == null) showError(getString(R.string.err_mod));
                else openFilePickerToSaveFile(getOriginalFileName(this, uriOfFileToBePatched).contains(".apk"));
            } else {
                if(PatchFileAsyncTask.fileToPatch.isEmpty()) showError(getString(R.string.err_orig));
                else if(PatchFileAsyncTask.originalFile.isEmpty()) showError(getString(R.string.err_mod));
                else new PatchFileAsyncTask(this).execute();
            }
        });
        ToggleButton onlyAMAndDexSwitch = findViewById(R.id.onlyAMAndDexSwitch);
        onlyAMAndDexSwitch.setChecked(PatchFileAsyncTask.onlyPatchAndroidManifestAndClasses);
        onlyAMAndDexSwitch.setOnClickListener(view -> PatchFileAsyncTask.onlyPatchAndroidManifestAndClasses = onlyAMAndDexSwitch.isChecked());
        ToggleButton setOldDateSwitch = findViewById(R.id.setOldDateSwitch);
        setOldDateSwitch.setChecked(PatchFileAsyncTask.restoreLastModifiedDate);
        setOldDateSwitch.setOnClickListener(view -> PatchFileAsyncTask.restoreLastModifiedDate = setOldDateSwitch.isChecked());
    }
    private void showSingleFileDialog(Uri sharedUri) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_single_file_layout, null);

        RadioButton radioFile1 = dialogView.findViewById(R.id.radioFile1);
        RadioButton radioFile2 = dialogView.findViewById(R.id.radioFile2);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setTitle(getString(R.string.ask));
        builder.setPositiveButton("OK", (dialog, which) -> {
            if (radioFile1.isChecked()) {
                uriOfOriginalFile = sharedUri;
                Toast.makeText(this, getString(R.string.select_modified), Toast.LENGTH_SHORT).show();
            } else if (radioFile2.isChecked()) {
                uriOfFileToBePatched = sharedUri;
                Toast.makeText(this, getString(R.string.select_original), Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void showSharedFilesDialog(ArrayList<Uri> sharedUris, boolean first) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_layout, null);
        ListView fileListView = dialogView.findViewById(R.id.fileListView);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setTitle(first ? getString(R.string.select_original) : getString(R.string.select_modified));
        if(!first) {
            builder.setPositiveButton("All", (dialogInterface, i) -> {
                for(int j = 0; j < sharedUris.size(); j++) {
                    uriOfFileToBePatched = sharedUris.get(j);
                    if(supportsInbuiltAndroidFilePicker) openFilePickerToSaveFile(getOriginalFileName(this, uriOfFileToBePatched).contains(".apk"));
                    else new PatchFileAsyncTask(this).execute(uriOfOriginalFile, uriOfFileToBePatched, Uri.fromFile(new File(uriOfFileToBePatched.getPath().replaceAll("\\.(apk|zip)", "_crc_restored.$1"))));
                }
            });
        }
        AlertDialog dialog = builder.create();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, getFileNamesFromUris(sharedUris));
        fileListView.setAdapter(adapter);
        fileListView.setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            if(first) {
                uriOfOriginalFile = sharedUris.get(position);
                sharedUris.remove(position);
                showSharedFilesDialog(sharedUris, false);
            }
            else {
                uriOfFileToBePatched = sharedUris.get(position);
                if(supportsInbuiltAndroidFilePicker) openFilePickerToSaveFile(getOriginalFileName(this, uriOfFileToBePatched).contains(".apk"));
                else new PatchFileAsyncTask(this).execute(uriOfOriginalFile, uriOfFileToBePatched, Uri.fromFile(new File(uriOfFileToBePatched.getPath().replaceAll("\\.(apk|zip)", "_crc_restored.$1"))));
            }
        });
        dialog.show();
    }
    private ArrayList<String> getFileNamesFromUris(ArrayList<Uri> uris) {
        ArrayList<String> fileNames = new ArrayList<>();
        for (Uri uri : uris) {
            String fileName = getOriginalFileName(this, uri);
            fileNames.add(fileName != null ? fileName : "Unknown File");
        }
        return fileNames;
    }
    @Override
    protected void onPause() {
        final SharedPreferences settings = getSharedPreferences("set", Context.MODE_PRIVATE);
        settings.edit().putBoolean("onlyPatchAMAndClasses", PatchFileAsyncTask.onlyPatchAndroidManifestAndClasses).putBoolean("restoreLastModifiedDate", PatchFileAsyncTask.restoreLastModifiedDate).apply();
        super.onPause();
    }

    private void openFilePickerToSelectFile(int requestCode) {
        if (supportsInbuiltAndroidFilePicker) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"application/vnd.android.package-archive", "application/zip", "application/octet-stream"});

            startActivityForResult(intent, requestCode);
        } else {
            final boolean isForOriginalFile = requestCode == 1;
            DialogProperties properties = new DialogProperties();
            properties.selection_mode = DialogConfigs.SINGLE_MODE;
            properties.selection_type = DialogConfigs.FILE_SELECT;
            properties.root = Environment.getExternalStorageDirectory();
            properties.error_dir = new File(DialogConfigs.DEFAULT_DIR);
            properties.offset = new File(DialogConfigs.DEFAULT_DIR);
            properties.extensions = new String[] {"apk", "zip"};
            FilePickerDialog dialog = new FilePickerDialog(MainActivity.this, properties, android.R.style.Theme_Black);
            dialog.setTitle(isForOriginalFile ? getString(R.string.select_original) : getString(R.string.select_modified));
            dialog.setDialogSelectionListener(files -> {
                if(isForOriginalFile) PatchFileAsyncTask.originalFile = files[0];
                else PatchFileAsyncTask.fileToPatch = files[0];
                dialog.dismiss();
            });
            dialog.show();
        }
    }
    private void openFilePickerToSaveFile(boolean isAPK) {
        Intent saveFileIntent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        saveFileIntent.addCategory(Intent.CATEGORY_OPENABLE);
        saveFileIntent.setType(isAPK ? "application/vnd.android.package-archive" : "application/zip");
        saveFileIntent.putExtra(Intent.EXTRA_TITLE, getOriginalFileName(this, uriOfFileToBePatched).replaceAll("\\.(apk|zip)", "_crc_restored.$1"));
        startActivityForResult(saveFileIntent, REQUEST_CODE_SAVE_FILE);
    }

    private void showError(String error) {
        runOnUiThread(() -> {
            TextView errorBox = findViewById(R.id.errorField);
            errorBox.setVisibility(View.VISIBLE);
            errorBox.setText(error);
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
        });
    }

    private String getOriginalFileName(Context context, Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                switch (requestCode) {
                    case REQUEST_CODE_SET_ORIGINAL_FILE:
                        uriOfOriginalFile = uri;
                    break;
                    case REQUEST_CODE_SET_FILE_TO_PATCH:
                        uriOfFileToBePatched = uri;
                    break;
                    case REQUEST_CODE_SAVE_FILE:
                        new PatchFileAsyncTask(this).execute(uriOfOriginalFile, uriOfFileToBePatched, uri);
                        break;
                }
            }
        }
    }

    private void toast(String message) {
        runOnUiThread(() ->  Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    static class PatchFileAsyncTask extends AsyncTask<Uri, Void, Void> {
        private static WeakReference<MainActivity> activityReference;
        // only retain a weak reference to the activity
        public PatchFileAsyncTask(MainActivity context) {
            activityReference = new WeakReference<>(context);
        }

        static boolean onlyPatchAndroidManifestAndClasses;
        static boolean restoreLastModifiedDate;
        static String originalFile;
        static String fileToPatch;

        private static long calculateCRC(InputStream stream) throws IOException {
            CRC32 crc = new CRC32();
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = stream.read(buffer)) != -1) {
                crc.update(buffer, 0, bytesRead);
            }
            return crc.getValue();
        }
        private static List<Map<String, Object>> getCRC(InputStream is) throws IOException {
            List<Map<String, Object>> res = new ArrayList<>();
            ZipInputStream zipStream = new ZipInputStream(is);
            LocalFileHeader entry;
            while (true) {
                entry = zipStream.getNextEntry();
                if (entry == null) break;
                final String fileName = entry.getFileName();
                if (!onlyPatchAndroidManifestAndClasses || fileName.contains(".dex") || fileName.contains("AndroidManifest.xml")) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", fileName);
                    map.put("crc", calculateCRC(zipStream));
                    res.add(map);
                }
            }
            return res;
        }
        private static void patchCRC(Uri uriOfOriginalFile, Uri uriOfFileToPatch, Map<byte[], byte[]> patches, FileOutputStream fos) throws IOException {
            MainActivity activity = activityReference.get();
            byte[] bytesToPatch = restoreLastModifiedDate ? restoreLastModifiedDates(new ZipInputStream(activity.getContentResolver().openInputStream(uriOfOriginalFile)), new ZipInputStream(activity.getContentResolver().openInputStream(uriOfFileToPatch)), new ZipInputStream(activity.getContentResolver().openInputStream(uriOfFileToPatch))) : readAllBytes(activity.getContentResolver().openInputStream(uriOfFileToPatch));
            for (Map.Entry<byte[], byte[]> patch : patches.entrySet()) {
                byte[] toReplace = patch.getKey();
                byte[] orig = patch.getValue();
                for (int i = 0; i < bytesToPatch.length - toReplace.length; i++) {
                    for (int j = 0; j < toReplace.length; j++) {
                        if (bytesToPatch[i + j] != toReplace[j]) {
                            break;
                        }
                        if (j == toReplace.length - 1) {
                            System.arraycopy(orig, 0, bytesToPatch, i, orig.length);
                        }
                    }
                }
            }
            fos.write(bytesToPatch);
            fos.flush();
            fos.close();
        }

        private static void patchCRC(Map<byte[], byte[]> patches, FileOutputStream fos) throws IOException {
            MainActivity activity = activityReference.get();
            byte[] bytesToPatch = restoreLastModifiedDate ? restoreLastModifiedDates(new ZipInputStream(activity.getContentResolver().openInputStream(uriOfOriginalFile)), new ZipInputStream(new FileInputStream(fileToPatch)), new ZipInputStream(new FileInputStream(fileToPatch))) : readAllBytes(new FileInputStream(originalFile));
            for (Map.Entry<byte[], byte[]> patch : patches.entrySet()) {
                byte[] toReplace = patch.getKey();
                byte[] orig = patch.getValue();
                for (int i = 0; i < bytesToPatch.length - toReplace.length; i++) {
                    for (int j = 0; j < toReplace.length; j++) {
                        if (bytesToPatch[i + j] != toReplace[j]) {
                            break;
                        }
                        if (j == toReplace.length - 1) {
                            System.arraycopy(orig, 0, bytesToPatch, i, orig.length);
                        }
                    }
                }
            }

            fos.write(bytesToPatch);
            fos.flush();
            fos.close();
        }

        private static byte[] toLittleEndian(long value) {
            byte[] bytes = new byte[4];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) (value >>> (i * 8));
            }
            return bytes;
        }

        private static byte[] readAllBytes(InputStream inputStream) throws IOException {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }
            return byteArrayOutputStream.toByteArray();
        }
        private static byte[] restoreLastModifiedDates(ZipInputStream originalZipInputStream, ZipInputStream patchedZipInputStream, ZipInputStream patchedZipInputStreamAgain) throws IOException {
            LocalFileHeader originalEntry;
            LocalFileHeader patchedEntry;

            Map<String, Long> lastModifiedMap = new HashMap<>();

            Map<String, Long> crcMap = new HashMap<>();

            while ((originalEntry = originalZipInputStream.getNextEntry()) != null && (patchedEntry = patchedZipInputStream.getNextEntry()) != null) {
                long lastModified = originalEntry.getLastModifiedTime();
                lastModifiedMap.put(patchedEntry.getFileName(), lastModified);
                crcMap.put(patchedEntry.getFileName(), originalEntry.getCrc());
            }

            originalZipInputStream.close();
            patchedZipInputStream.close();
            ByteArrayOutputStream bs = new ByteArrayOutputStream();
            net.lingala.zip4j.io.outputstream.ZipOutputStream zipOutputStream = new net.lingala.zip4j.io.outputstream.ZipOutputStream(bs);

            byte[] buffer = new byte[1024];
            int bytesRead;
            LocalFileHeader entry;
            while ((entry = patchedZipInputStreamAgain.getNextEntry()) != null) {
                ZipParameters zs = new ZipParameters();
                final String name = entry.getFileName();
                zs.setFileNameInZip(name);
                final CompressionMethod cm = entry.getCompressionMethod();
                zs.setCompressionMethod(cm);
                if(cm.equals(CompressionMethod.STORE)) zs.setEntrySize(entry.getUncompressedSize());
                zs.setEntryCRC(crcMap.get(name)); // This does not actually set the old CRC but you need to do this otherwise zip4j creates a new CRC if the file was already modified and then the byte patch doesn't work because it didn't look for that CRC. This is another reason to use ZipFile
                zs.setLastModifiedFileTime(lastModifiedMap.get(name));
                zipOutputStream.putNextEntry(zs);


                while ((bytesRead = patchedZipInputStreamAgain.read(buffer)) != -1) {
                    zipOutputStream.write(buffer, 0, bytesRead);
                }

                zipOutputStream.closeEntry();
            }

            patchedZipInputStreamAgain.close();
            zipOutputStream.close();
            return bs.toByteArray();
        }

        @Override
        protected Void doInBackground(Uri... uris) {
            MainActivity activity = activityReference.get();
            activity.runOnUiThread(() -> activity.findViewById(R.id.errorField).setVisibility(View.INVISIBLE));

            try {
                InputStream originalFileStream;
                InputStream toPatchFileStream;
                FileOutputStream outputFileStream;

                if(supportsInbuiltAndroidFilePicker) {
                    final Uri uriOfOriginalFile = uris[0];
                    final Uri uriOfFileToPatch = uris[1];
                    final Uri outputUri = uris[2];
                    originalFileStream = activity.getContentResolver().openInputStream(uriOfOriginalFile);
                    toPatchFileStream = activity.getContentResolver().openInputStream(uriOfFileToPatch);
                    outputFileStream = (FileOutputStream) activity.getContentResolver().openOutputStream(outputUri);
                } else {
                    originalFileStream = new FileInputStream(originalFile);
                    toPatchFileStream = new FileInputStream(fileToPatch);
                    outputFileStream = new FileOutputStream(fileToPatch.replaceAll("\\.(apk|zip)", "_crc_restored.$1"));
                }

                List<Map<String, Object>> originalCrc = getCRC(originalFileStream);
                List<Map<String, Object>> crcToBeReplaced = getCRC(toPatchFileStream);
                Map<byte[], byte[]> patches = new HashMap<>();

                for (int i = 0; i < originalCrc.size(); i++) {
                    Map<String, Object> orig = originalCrc.get(i);
                    Map<String, Object> fix = crcToBeReplaced.get(i);
                    if (!orig.get("crc").equals(fix.get("crc"))) {
                        byte[] originalCRCBytes = toLittleEndian((Long) orig.get("crc"));
                        byte[] crcBytesToPatch = toLittleEndian((Long) fix.get("crc"));
                        patches.put(crcBytesToPatch, originalCRCBytes);
                    }
                }
                if (patches.isEmpty()) activity.showError(activity.getString(R.string.no_change));
                else {
                    if(supportsInbuiltAndroidFilePicker) patchCRC(uriOfOriginalFile, uris[1], patches, outputFileStream);
                    else patchCRC(patches, outputFileStream);
                    activity.toast(activity.getString(R.string.success));
                }
            } catch (IOException e) {
                activity.showError(Arrays.toString(e.getStackTrace()));
            }
            return null;
        }
    }
}