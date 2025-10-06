import { DocumentScanner, ScanDocumentResponseStatus, ScannerMode, ResponseType } from '@luuckyy/capacitor-document-scanner'

/**
 * an example showing how to use the document scanner with Google ML Kit
 */
const scanDocument = async (): Promise<void> => {
  console.log('=== Document Scanner Test Started ===')
  
  try {
    console.log('Calling DocumentScanner.scanDocument with options:')
    const options = {
      // Maximum number of documents to scan
      maxNumDocuments: 1,
      
      // Scanner mode - FULL provides all features (filters, detection, cropping)
      // Other options: ScannerMode.BASE, ScannerMode.BASE_WITH_FILTER
      scannerMode: ScannerMode.FULL,
      
      // Response type - can be base64 or file path
      responseType: ResponseType.Base64,
      
      // Allow user to adjust detected document corners
      letUserAdjustCrop: true,
      
      // Image quality (0-100, only affects base64 responses)
      croppedImageQuality: 100
    }
    
    // start the document scanner with ML Kit options
    const result = await DocumentScanner.scanDocument(options)

    
    const { scannedImages, status } = result

  
    // get the html image
    const scannedImage = document.getElementById('scannedImage') as HTMLImageElement
    
    if (status === ScanDocumentResponseStatus.Success && scannedImages?.length) {
      // Handle different response types
      const firstImage = scannedImages[0]
      console.log(firstImage)
      
      scannedImage.src = `data:image/jpeg;base64,${firstImage}`
      console.log('Set image src to base64 data (length):', firstImage.length)

      // show the scanned image
      scannedImage.style.display = 'block'
      console.log('Image displayed successfully')
      alert('document scanned successfully')
    } else if (status === ScanDocumentResponseStatus.Cancel) {
      console.log('User cancelled the scan')
      // user exited camera
      alert('user canceled document scan')
    } else {
      console.log('Unexpected result state')
      console.log('Status:', status)
      console.log('Scanned images:', scannedImages)
      alert('Unexpected result: ' + JSON.stringify(result))
    }
  } catch (error) {
    console.error('=== Document Scanner Error ===')
    console.error('Error type:', typeof error)
    console.error('Error message:', error)
    console.error('Error stack:', error instanceof Error ? error.stack : 'No stack trace')
    // something went wrong during the document scan
    alert('Error: ' + error)
  }
  
  console.log('=== Document Scanner Test Completed ===')
}

scanDocument()