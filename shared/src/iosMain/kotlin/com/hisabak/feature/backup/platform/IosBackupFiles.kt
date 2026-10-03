package com.hisabak.feature.backup.platform

import com.hisabak.core.data.backup.toByteArray
import com.hisabak.core.data.backup.toNSData
import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.darwin.NSObject

/**
 * The iOS side of backup file export/import: the system document picker, which reaches Files,
 * iCloud Drive, and any provider app without new entitlements. Both pickers run `asCopy`, so
 * an export copies out of a temp file and an import hands back a copy inside the sandbox — no
 * security-scoped bookmark to manage.
 */
@OptIn(ExperimentalForeignApi::class)
internal object IosBackupFiles {

    // UIDocumentPickerViewController holds its delegate weakly; this keeps it alive meanwhile.
    private var activeDelegate: PickerDelegate? = null

    /** Lets the user save [bytes] as [fileName]. False when they cancel; throws if the copy can't be staged. */
    suspend fun export(fileName: String, bytes: ByteArray): Boolean {
        val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + fileName)
        check(bytes.toNSData().writeToURL(url, atomically = true)) { "Couldn't stage the export" }
        return try {
            present(UIDocumentPickerViewController(forExportingURLs = listOf(url), asCopy = true)) != null
        } finally {
            NSFileManager.defaultManager.removeItemAtURL(url, error = null)
        }
    }

    /** Lets the user pick a backup file; null when they cancel. */
    suspend fun pick(): NSURL? =
        present(UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeData), asCopy = true))

    fun read(url: NSURL): ByteArray {
        val data = checkNotNull(NSData.dataWithContentsOfURL(url)) { "Couldn't read the picked file" }
        NSFileManager.defaultManager.removeItemAtURL(url, error = null) // our copy; the original stays
        return data.toByteArray()
    }

    private suspend fun present(picker: UIDocumentPickerViewController): NSURL? =
        suspendCancellableCoroutine { cont ->
            val delegate = PickerDelegate { url ->
                activeDelegate = null
                if (cont.isActive) cont.resume(url)
            }
            activeDelegate = delegate
            picker.delegate = delegate
            picker.allowsMultipleSelection = false
            val host = topViewController()
            if (host == null) {
                activeDelegate = null
                cont.resume(null)
            } else {
                host.presentViewController(picker, animated = true, completion = null)
                cont.invokeOnCancellation { picker.dismissViewControllerAnimated(true, completion = null) }
            }
        }

    private fun topViewController(): UIViewController? {
        var top = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (top?.presentedViewController != null) top = top.presentedViewController
        return top
    }
}

private class PickerDelegate(
    private val onDone: (NSURL?) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        onDone(didPickDocumentsAtURLs.firstOrNull() as? NSURL)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onDone(null)
    }
}
