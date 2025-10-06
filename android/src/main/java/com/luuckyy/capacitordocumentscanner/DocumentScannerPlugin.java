package com.luuckyy.capacitordocumentscanner;

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
@CapacitorPlugin(name = "DocumentScanner", requestCodes = {9999})
public class DocumentScannerPlugin extends Plugin {

    private static final String TAG = "DocumentScannerPlugin";
    private static final int REQUEST_CODE_SCAN = 9999;
    
    // Response type constants
    private static final String RESPONSE_TYPE_IMAGE_FILE_PATH = "imageFilePath";
    private static final String RESPONSE_TYPE_BASE64 = "base64";
    
    // Scanner mode constants
    private static final String SCANNER_MODE_FULL = "FULL";
    private static final String SCANNER_MODE_BASE = "BASE";
    private static final String SCANNER_MODE_BASE_WITH_FILTER = "BASE_WITH_FILTER";
    
    // Store parameters for use in result handling
    private String currentResponseType = RESPONSE_TYPE_IMAGE_FILE_PATH;
    private int currentQuality = 100;

    /**
     * start the document scanner using Google ML Kit
     *
     * @param call contains JS inputs and lets you return results
     */
    @PluginMethod
    public void scanDocument(PluginCall call) {
        Log.d(TAG, "=== scanDocument called ===");
        Log.d(TAG, "Call ID: " + call.getCallbackId());
        
        // Get configuration options from the call
        Integer maxNumDocuments = call.getInt("maxNumDocuments", 24);
        String scannerMode = call.getString("scannerMode", SCANNER_MODE_FULL);
        String responseType = call.getString("responseType", RESPONSE_TYPE_IMAGE_FILE_PATH);
        Integer quality = call.getInt("croppedImageQuality", 100);
        Boolean letUserAdjustCrop = call.getBoolean("letUserAdjustCrop", true);
        
        Log.d(TAG, "Parameters:");
        Log.d(TAG, "  maxNumDocuments: " + maxNumDocuments);
        Log.d(TAG, "  scannerMode: " + scannerMode);
        Log.d(TAG, "  responseType: " + responseType);
        Log.d(TAG, "  quality: " + quality);
        Log.d(TAG, "  letUserAdjustCrop: " + letUserAdjustCrop);
        
        // Store parameters for use in result handling
        this.currentResponseType = responseType;
        this.currentQuality = quality != null ? quality : 100;
        
        // Build ML Kit scanner options
        GmsDocumentScannerOptions.Builder optionsBuilder = new GmsDocumentScannerOptions.Builder();
        
        // Set scanner mode
        if (SCANNER_MODE_BASE.equals(scannerMode)) {
            optionsBuilder.setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE);
            Log.d(TAG, "Scanner mode set to BASE");
        } else if (SCANNER_MODE_BASE_WITH_FILTER.equals(scannerMode)) {
            optionsBuilder.setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE_WITH_FILTER);
            Log.d(TAG, "Scanner mode set to BASE_WITH_FILTER");
        } else {
            optionsBuilder.setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL);
            Log.d(TAG, "Scanner mode set to FULL");
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
        optionsBuilder.setGalleryImportAllowed(letUserAdjustCrop);
        Log.d(TAG, "Gallery import allowed: " + letUserAdjustCrop);
        
        GmsDocumentScannerOptions options = optionsBuilder.build();
        GmsDocumentScanner scanner = GmsDocumentScanning.getClient(options);
        
        // Get the scanner intent
        Log.d(TAG, "Getting scanner intent...");
        scanner.getStartScanIntent(getActivity())
            .addOnSuccessListener(intentSender -> {
                Log.d(TAG, "Scanner intent obtained successfully");
                try {
                    // Save the call to process the result later
                    saveCall(call);
                    Log.d(TAG, "Call saved, starting scanner with request code: " + REQUEST_CODE_SCAN);
                    
                    // Use traditional startIntentSenderForResult with declared request code
                    getActivity().startIntentSenderForResult(
                        intentSender,
                        REQUEST_CODE_SCAN, // Use the request code declared in annotation
                        null, // No fill-in intent
                        0,    // flagsMask
                        0,    // flagsValues
                        0     // extraFlags
                    );
                    Log.d(TAG, "Scanner started successfully with request code: " + REQUEST_CODE_SCAN);
                } catch (Exception e) {
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
        Log.d(TAG, "=== handleOnActivityResult called ===");
        Log.d(TAG, "Request code: " + requestCode + " (expected: " + REQUEST_CODE_SCAN + ")");
        Log.d(TAG, "Result code: " + resultCode + " (RESULT_OK=" + Activity.RESULT_OK + ", RESULT_CANCELED=" + Activity.RESULT_CANCELED + ")");
        Log.d(TAG, "Data is null: " + (data == null));
        
        super.handleOnActivityResult(requestCode, resultCode, data);
        
        if (requestCode != REQUEST_CODE_SCAN) {
            Log.d(TAG, "Request code mismatch - expected: " + REQUEST_CODE_SCAN + ", got: " + requestCode);
            return;
        }
        
        PluginCall call = getSavedCall();
        if (call == null) {
            Log.e(TAG, "No saved call found - this should not happen");
            return;
        }
        
        Log.d(TAG, "Processing result with call: " + call.getCallbackId());
        JSObject response = new JSObject();
        
        if (resultCode == Activity.RESULT_CANCELED) {
            // User cancelled the scan
            Log.d(TAG, "User cancelled the scan - returning cancel status");
            response.put("status", "cancel");
            call.resolve(response);
            Log.d(TAG, "Call resolved with cancel status");
            return;
        }
        
        if (resultCode != Activity.RESULT_OK) {
            // Error occurred
            Log.e(TAG, "Document scanning failed with result code: " + resultCode);
            call.reject("Document scanning failed with result code: " + resultCode);
            return;
        }
        
        if (data == null) {
            Log.e(TAG, "No data returned from document scanner");
            call.reject("No data returned from document scanner");
            return;
        }
        
        Log.d(TAG, "Data received successfully, extracting scanning result...");
        
        // Extract scanning result
        GmsDocumentScanningResult scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(data);
        if (scanningResult == null) {
            Log.e(TAG, "Failed to extract scanning result from intent data");
            call.reject("Failed to extract scanning result");
            return;
        }
        
        Log.d(TAG, "Scanning result extracted successfully");
        
        // Process the scanned pages
        List<GmsDocumentScanningResult.Page> pages = scanningResult.getPages();
        if (pages == null || pages.isEmpty()) {
            Log.e(TAG, "No pages were scanned - pages is null or empty");
            call.reject("No pages were scanned");
            return;
        }
        
        Log.d(TAG, "Found " + pages.size() + " scanned pages");
        Log.d(TAG, "Processing images with responseType: " + this.currentResponseType + ", quality: " + this.currentQuality);
        
        try {
            ArrayList<String> scannedImages = new ArrayList<>();
            
            for (int i = 0; i < pages.size(); i++) {
                GmsDocumentScanningResult.Page page = pages.get(i);
                Log.d(TAG, "Processing page " + (i + 1) + " of " + pages.size());
                
                Uri imageUri = page.getImageUri();
                if (imageUri != null) {
                    Log.d(TAG, "Page " + (i + 1) + " image URI: " + imageUri.toString());
                    String processedImage = processImage(imageUri, this.currentResponseType, this.currentQuality);
                    if (processedImage != null) {
                        scannedImages.add(processedImage);
                        Log.d(TAG, "Page " + (i + 1) + " processed successfully, length: " + processedImage.length());
                    } else {
                        Log.e(TAG, "Failed to process page " + (i + 1));
                    }
                } else {
                    Log.e(TAG, "Page " + (i + 1) + " has null image URI");
                }
            }
            
            Log.d(TAG, "Processed " + scannedImages.size() + " images successfully");
            
            if (scannedImages.isEmpty()) {
                Log.e(TAG, "No images were processed successfully");
                call.reject("Failed to process scanned images");
                return;
            }
            
            response.put("scannedImages", new JSArray(scannedImages));
            response.put("status", "success");
            
            Log.d(TAG, "Resolving call with success status and " + scannedImages.size() + " images");
            call.resolve(response);
            Log.d(TAG, "Call resolved successfully");
            
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
            if (RESPONSE_TYPE_IMAGE_FILE_PATH.equals(responseType)) {
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