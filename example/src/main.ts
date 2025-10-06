import { Capacitor } from '@capacitor/core'
import { DocumentScanner, ScanDocumentResponseStatus, ScannerMode, ResponseType } from 'capacitor-document-scanner'

/**
 * an example showing how to use the document scanner with Google ML Kit
 */
const scanDocument = async (): Promise<void> => {
  try {
    // start the document scanner with ML Kit options
    const { scannedImages, status } = await DocumentScanner.scanDocument({
      // Maximum number of documents to scan
      maxNumDocuments: 1,
      
      // Scanner mode - FULL provides all features (filters, detection, cropping)
      // Other options: ScannerMode.BASE, ScannerMode.BASE_WITH_FILTER
      scannerMode: ScannerMode.FULL,
      
      // Response type - can be base64 or file path
      responseType: ResponseType.ImageFilePath,
      
      // Allow user to adjust detected document corners
      letUserAdjustCrop: true,
      
      // Image quality (0-100, only affects base64 responses)
      croppedImageQuality: 100
    })
  
    // get the html image
    const scannedImage = document.getElementById('scannedImage') as HTMLImageElement
  
    if (status === ScanDocumentResponseStatus.Success && scannedImages?.length) {
      // set the image src to the scanned image file path
      scannedImage.src = Capacitor.convertFileSrc(scannedImages[0])

      // show the scanned image
      scannedImage.style.display = 'block'
    } else if (status === ScanDocumentResponseStatus.Cancel) {
      // user exited camera
      alert('user canceled document scan')
    }
  } catch (error) {
    // something went wrong during the document scan
    alert(error)
  }
}

scanDocument()