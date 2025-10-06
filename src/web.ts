import { WebPlugin } from '@capacitor/core';

import type { 
  DocumentScannerPlugin, 
  ScanDocumentOptions, 
  ScanDocumentResponse 
} from './definitions';

export class DocumentScannerWeb
  extends WebPlugin
  implements DocumentScannerPlugin {
  async scanDocument(options?: ScanDocumentOptions): Promise<ScanDocumentResponse> {
    console.log('=== DocumentScannerWeb.scanDocument called ===')
    console.log('Options received:', options)
    console.log('Options type:', typeof options)
    if (options) {
      console.log('Options keys:', Object.keys(options))
      console.log('maxNumDocuments:', options.maxNumDocuments)
      console.log('scannerMode:', options.scannerMode)
      console.log('responseType:', options.responseType)
      console.log('letUserAdjustCrop:', options.letUserAdjustCrop)
      console.log('croppedImageQuality:', options.croppedImageQuality)
    }
    console.log('Throwing unimplemented error for web platform')
    throw this.unimplemented('Not implemented on web.');
  }
}