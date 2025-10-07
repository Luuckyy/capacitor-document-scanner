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
        
        // Get configuration options from the call
        Integer maxNumDocuments = call.getInt("maxNumDocuments", 24);
        String scannerMode = call.getString("scannerMode", SCANNER_MODE_FULL);
        String responseType = call.getString("responseType", RESPONSE_TYPE_IMAGE_FILE_PATH);
        Integer quality = call.getInt("croppedImageQuality", 100);
        Boolean letUserAdjustCrop = call.getBoolean("letUserAdjustCrop", true);
        
        // Store parameters for use in result handling
        this.currentResponseType = responseType;
        this.currentQuality = quality != null ? quality : 100;
        
        // Build ML Kit scanner options
        GmsDocumentScannerOptions.Builder optionsBuilder = new GmsDocumentScannerOptions.Builder();
        
        // Set scanner mode
        if (SCANNER_MODE_BASE.equals(scannerMode)) {
            optionsBuilder.setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE);
        } else if (SCANNER_MODE_BASE_WITH_FILTER.equals(scannerMode)) {
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
        optionsBuilder.setGalleryImportAllowed(letUserAdjustCrop);
        
        GmsDocumentScannerOptions options = optionsBuilder.build();
        GmsDocumentScanner scanner = GmsDocumentScanning.getClient(options);
        
        // Get the scanner intent
        scanner.getStartScanIntent(getActivity())
            .addOnSuccessListener(intentSender -> {
                try {
                    // Save the call to process the result later
                    saveCall(call);

                    // Use traditional startIntentSenderForResult with declared request code
                    getActivity().startIntentSenderForResult(
                        intentSender,
                        REQUEST_CODE_SCAN, // Use the request code declared in annotation
                        null, // No fill-in intent
                        0,    // flagsMask
                        0,    // flagsValues
                        0     // extraFlags
                    );

                } catch (Exception e) {
                    call.reject("Failed to start document scanner: " + e.getMessage());
                }
            })
            .addOnFailureListener(e -> {
                call.reject("Failed to initialize document scanner: " + e.getMessage());
            });
    }

    @Override
    protected void handleOnActivityResult(int requestCode, int resultCode, Intent data) {
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

        try {
            ArrayList<String> scannedImages = new ArrayList<>();
            
            for (int i = 0; i < pages.size(); i++) {
                GmsDocumentScanningResult.Page page = pages.get(i);
                
                Uri imageUri = page.getImageUri();
                if (imageUri != null) {
                    String processedImage = processImage(imageUri, this.currentResponseType, this.currentQuality);
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
                // Use external files directory which is accessible and doesn't require permissions
                File picturesDir = getContext().getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES);
                if (picturesDir == null) {
                    // Fallback to external files directory root
                    picturesDir = getContext().getExternalFilesDir(null);
                }
                
                if (picturesDir == null) {
                    Log.e(TAG, "Failed to get external files directory");
                    return null;
                }
                
                // Create the directory if it doesn't exist
                if (!picturesDir.exists()) {
                    picturesDir.mkdirs();
                }
                
                // Copy to app's external files directory and return Capacitor-compatible URI
                File imageFile = new File(picturesDir, "scanned_" + System.currentTimeMillis() + ".jpg");
                copyUriToFile(imageUri, imageFile);
                
                // Make the file readable
                imageFile.setReadable(true, false);
                
                // Convert to Capacitor WebView URL that can be accessed from the web layer
                String fileUri = imageFile.toURI().toString();
                
                // Use Capacitor's bridge to get a web-accessible URL
                if (getBridge() != null && getBridge().getLocalUrl() != null) {
                    String webViewUrl = getBridge().getLocalUrl();
                    // Convert file path to a capacitor:// URL
                    String capacitorPath = fileUri.replace("file://", webViewUrl + "/_capacitor_file_");
                    return capacitorPath;
                }
                
                // Fallback to file:// URI if bridge is not available
                return fileUri;
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