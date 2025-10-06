package com.websitebeaver.capacitordocumentscanner;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner;
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is used to start a document scan using Google ML Kit.
 * It accepts parameters used to customize the document scan, and callback parameters.
 */
@CapacitorPlugin(name = "DocumentScanner")
public class DocumentScannerPlugin extends Plugin {

    private static final String TAG = "DocumentScannerPlugin";
    private static final int REQUEST_CODE_SCAN = 9999;

    /**
     * start the document scanner using Google ML Kit
     *
     * @param call contains JS inputs and lets you return results
     */
    @PluginMethod
    public void scanDocument(PluginCall call) {
        // Get configuration options from the call
        String responseType = call.getString("responseType", "base64");
        Integer maxNumDocuments = call.getInt("maxNumDocuments", 1);
        String scannerMode = call.getString("scannerMode", "FULL");
        
        // Build ML Kit scanner options
        GmsDocumentScannerOptions.Builder optionsBuilder = new GmsDocumentScannerOptions.Builder();
        
        // Set scanner mode
        if ("BASE".equals(scannerMode)) {
            optionsBuilder.setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE);
        } else if ("BASE_WITH_FILTER".equals(scannerMode)) {
            optionsBuilder.setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE_WITH_FILTER);
        } else {
            optionsBuilder.setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL);
        }
        
        // Set result formats
        optionsBuilder.setResultFormats(
            GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
            GmsDocumentScannerOptions.RESULT_FORMAT_PDF
        );
        
        // Set page limit
        if (maxNumDocuments != null && maxNumDocuments > 0) {
            optionsBuilder.setPageLimit(maxNumDocuments);
        }
        
        // Set gallery import allowed (equivalent to letUserAdjustCrop)
        optionsBuilder.setGalleryImportAllowed(call.getBoolean("letUserAdjustCrop", true));
        
        GmsDocumentScannerOptions options = optionsBuilder.build();
        GmsDocumentScanner scanner = GmsDocumentScanning.getClient(options);
        
        // Get the scanner intent
        scanner.getStartScanIntent(getActivity())
            .addOnSuccessListener(intentSender -> {
                try {
                    // Create an intent from the intent sender
                    Intent intent = new Intent();
                    // Store the call for later use in the callback
                    // Launch the scanner using the IntentSender directly via activity
                    getActivity().startIntentSenderForResult(
                        intentSender,
                        REQUEST_CODE_SCAN,
                        intent,
                        0,
                        0,
                        0
                    );
                    // Save the call to process the result later
                    saveCall(call);
                } catch (IntentSender.SendIntentException e) {
                    Log.e(TAG, "Error starting scanner", e);
                    call.reject("Failed to start document scanner: " + e.getMessage());
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error getting scanner intent", e);
                call.reject("Failed to initialize document scanner: " + e.getMessage());
            });
    }

    @Override
    protected void handleOnActivityResult(int requestCode, int resultCode, Intent data) {
        super.handleOnActivityResult(requestCode, resultCode, data);
        
        if (requestCode != REQUEST_CODE_SCAN) {
            return;
        }
        
        PluginCall call = getSavedCall();
        if (call == null) {
            return;
        }
        
        JSObject response = new JSObject();
        
        if (resultCode == Activity.RESULT_CANCELED) {
            // User cancelled the scan
            response.put("status", "cancel");
            call.resolve(response);
            return;
        }
        
        if (resultCode != Activity.RESULT_OK) {
            // Error occurred
            call.reject("Document scanning failed with result code: " + resultCode);
            return;
        }
        
        if (data == null) {
            call.reject("No data returned from document scanner");
            return;
        }
        
        // Extract scanning result
        GmsDocumentScanningResult scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(data);
        if (scanningResult == null) {
            call.reject("Failed to extract scanning result");
            return;
        }
        
        // Process the scanned pages
        List<GmsDocumentScanningResult.Page> pages = scanningResult.getPages();
        if (pages == null || pages.isEmpty()) {
            call.reject("No pages were scanned");
            return;
        }
        
        String responseType = call.getString("responseType", "base64");
        Integer quality = call.getInt("croppedImageQuality", 100);
        if (quality == null || quality < 0 || quality > 100) {
            quality = 100;
        }
        
        try {
            ArrayList<String> scannedImages = new ArrayList<>();
            
            for (GmsDocumentScanningResult.Page page : pages) {
                Uri imageUri = page.getImageUri();
                if (imageUri != null) {
                    String processedImage = processImage(imageUri, responseType, quality);
                    if (processedImage != null) {
                        scannedImages.add(processedImage);
                    }
                }
            }
            
            if (scannedImages.isEmpty()) {
                call.reject("Failed to process scanned images");
                return;
            }
            
            response.put("scannedImages", new JSArray(scannedImages));
            response.put("status", "success");
            call.resolve(response);
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing scanned images", e);
            call.reject("Failed to process scanned images: " + e.getMessage());
        }
    }
    
    /**
     * Process the scanned image based on response type
     *
     * @param imageUri URI of the scanned image
     * @param responseType "base64" or "imageUri"
     * @param quality JPEG quality (0-100)
     * @return processed image string
     */
    private String processImage(Uri imageUri, String responseType, int quality) {
        try {
            if ("imageFilePath".equals(responseType) || "imageUri".equals(responseType)) {
                // Copy to app's cache directory and return file URI
                File cacheFile = new File(getContext().getCacheDir(), "scanned_" + System.currentTimeMillis() + ".jpg");
                copyUriToFile(imageUri, cacheFile);
                return cacheFile.getAbsolutePath();
            } else {
                // Convert to base64
                InputStream inputStream = getContext().getContentResolver().openInputStream(imageUri);
                if (inputStream == null) {
                    return null;
                }
                
                // Decode bitmap
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                inputStream.close();
                
                if (bitmap == null) {
                    return null;
                }
                
                // Compress to JPEG
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream);
                byte[] imageBytes = outputStream.toByteArray();
                bitmap.recycle();
                
                // Encode to base64
                return Base64.encodeToString(imageBytes, Base64.NO_WRAP);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error processing image", e);
            return null;
        }
    }
    
    /**
     * Copy content from URI to a file
     *
     * @param sourceUri source URI
     * @param destFile destination file
     */
    private void copyUriToFile(Uri sourceUri, File destFile) throws IOException {
        InputStream inputStream = getContext().getContentResolver().openInputStream(sourceUri);
        if (inputStream == null) {
            throw new IOException("Failed to open input stream");
        }
        
        FileOutputStream outputStream = new FileOutputStream(destFile);
        byte[] buffer = new byte[4096];
        int bytesRead;
        
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        
        outputStream.close();
        inputStream.close();
    }
}