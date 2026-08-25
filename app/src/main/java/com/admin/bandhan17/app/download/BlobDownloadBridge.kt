package com.admin.bandhan17.app.download

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView

/**
 * JavaScript interface bridge exposed to WebView as 'AndroidBlobDownloader'.
 * Allows the web app and injected script to pass generated Blob / Data URL files
 * directly into the native download engine with user confirmation.
 */
class BlobDownloadBridge(
    private val context: Context,
    private val onDownloadRequested: (DownloadRequest) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun getBase64FromBlobData(base64Data: String?, fileName: String?, mimeType: String?) {
        if (base64Data.isNullOrBlank()) return

        val resolvedMime = DownloadHandler.resolveMimeType("", mimeType, null)
        val resolvedFileName = DownloadHandler.resolveBlobFileName(fileName, resolvedMime)

        val request = DownloadRequest(
            url = "blob:data",
            suggestedFileName = resolvedFileName,
            mimeType = resolvedMime,
            base64Data = base64Data,
            isBlobOrBase64 = true
        )

        mainHandler.post {
            onDownloadRequested(request)
        }
    }

    @JavascriptInterface
    fun notifyDownloadStarted(fileName: String?) {
        // Logging / UI hook if needed
    }

    companion object {
        const val JS_INTERFACE_NAME = "AndroidBlobDownloader"

        /**
         * Converts a blob URL or data URL in the webview to a Base64 stream and passes it to the bridge.
         */
        fun downloadBlobUrl(
            webView: WebView,
            blobOrDataUrl: String,
            suggestedFileName: String?,
            mimeType: String?
        ) {
            val safeFileName = (suggestedFileName ?: "Bandhan17_Download").replace("'", "\\'")
            val safeMime = (mimeType ?: "application/octet-stream").replace("'", "\\'")
            val safeUrl = blobOrDataUrl.replace("'", "\\'")

            val jsCode = """
                (function() {
                    try {
                        var url = '$safeUrl';
                        var name = '$safeFileName';
                        var mime = '$safeMime';
                        
                        if (url.indexOf('data:') === 0) {
                            if (window.$JS_INTERFACE_NAME) {
                                window.$JS_INTERFACE_NAME.getBase64FromBlobData(url, name, mime);
                            }
                            return;
                        }
                        
                        fetch(url)
                            .then(function(response) { return response.blob(); })
                            .then(function(blob) {
                                var reader = new FileReader();
                                reader.onloadend = function() {
                                    if (window.$JS_INTERFACE_NAME) {
                                        window.$JS_INTERFACE_NAME.getBase64FromBlobData(
                                            reader.result, 
                                            name, 
                                            blob.type || mime || 'application/octet-stream'
                                        );
                                    }
                                };
                                reader.readAsDataURL(blob);
                            })
                            .catch(function(err) {
                                console.error('Error fetching blob URL in WebView', err);
                            });
                    } catch (e) {
                        console.error('Blob downloader exception', e);
                    }
                })();
            """.trimIndent()

            webView.post {
                webView.evaluateJavascript(jsCode, null)
            }
        }

        /**
         * Global JavaScript hook injected on page load to intercept client-side downloads (<a download> and blob clicks).
         */
        val INTERCEPTOR_JS = """
            (function() {
                if (window.__bandhanDownloadHookInjected) return;
                window.__bandhanDownloadHookInjected = true;

                document.addEventListener('click', function(e) {
                    var target = e.target;
                    while (target && target.tagName !== 'A') {
                        target = target.parentElement;
                    }
                    if (!target || !target.href) return;
                    
                    var href = target.href;
                    var downloadAttr = target.getAttribute('download');
                    
                    if (href.indexOf('blob:') === 0 || href.indexOf('data:') === 0 || downloadAttr !== null) {
                        if (href.indexOf('blob:') === 0 || href.indexOf('data:') === 0) {
                            var filename = downloadAttr || target.download || 'Bandhan17_Download';
                            if (window.$JS_INTERFACE_NAME) {
                                e.preventDefault();
                                e.stopPropagation();
                                fetch(href)
                                    .then(function(r) { return r.blob(); })
                                    .then(function(blob) {
                                        var reader = new FileReader();
                                        reader.onloadend = function() {
                                            window.$JS_INTERFACE_NAME.getBase64FromBlobData(
                                                reader.result,
                                                filename,
                                                blob.type || 'application/octet-stream'
                                            );
                                        };
                                        reader.readAsDataURL(blob);
                                    })
                                    .catch(function(err) {
                                        console.error('Interceptor fetch error', err);
                                    });
                            }
                        }
                    }
                }, true);
            })();
        """.trimIndent()
    }
}
