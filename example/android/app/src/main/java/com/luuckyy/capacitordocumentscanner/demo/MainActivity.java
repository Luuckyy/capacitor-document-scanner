package com.luuckyy.capacitordocumentscanner.demo;

import com.getcapacitor.BridgeActivity;
import com.luuckyy.capacitordocumentscanner.DocumentScannerPlugin;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Register the DocumentScanner plugin
        registerPlugin(DocumentScannerPlugin.class);
    }
}