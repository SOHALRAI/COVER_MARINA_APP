package com.marina.publications.festivalportal;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.webkit.JavascriptInterface;
import android.content.pm.PackageManager;
import android.provider.OpenableColumns;
import android.webkit.DownloadListener;

import androidx.core.content.FileProvider;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String SITE_URL = "https://cover.marinapublications.com/";
    private static final String ALLOWED_HOST = "cover.marinapublications.com";
    private static final int FILE_CHOOSER_REQUEST = 4102;
    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService shareExecutor = Executors.newSingleThreadExecutor();

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setUserAgentString(settings.getUserAgentString() + " MarinaFestivalAndroid/1.0");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);

        webView.addJavascriptInterface(new NativeShareBridge(), "MarinaNativeShare");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost();
                if (host != null && (host.equals(ALLOWED_HOST) || host.equals("www." + ALLOWED_HOST))) {
                    return false;
                }
                // Keep external links out of the embedded portal.
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                installNativeShareHook(view);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                try {
                    Intent intent = params.createIntent();
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                    return true;
                } catch (Exception e) {
                    filePathCallback = null;
                    Toast.makeText(MainActivity.this, "File picker could not be opened", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            // Existing portal's Download button can still be used in the WebView.
            Toast.makeText(this, "Use the website's Download button to save this file.", Toast.LENGTH_SHORT).show();
        });

        webView.loadUrl(SITE_URL);
    }

    private void installNativeShareHook(WebView view) {
        String js = "(function(){"
                + "if(window.__marinaNativeShareHooked)return;window.__marinaNativeShareHooked=true;"
                + "document.addEventListener('click',function(e){"
                + "var b=e.target.closest?e.target.closest('.share-btn'):null;if(!b)return;"
                + "e.preventDefault();e.stopPropagation();if(e.stopImmediatePropagation)e.stopImmediatePropagation();"
                + "var u=b.getAttribute('data-url');var t=b.getAttribute('data-title')||'Festival media';"
                + "if(u&&window.MarinaNativeShare){window.MarinaNativeShare.share(new URL(u,location.href).href,t);}"
                + "},true);"
                + "})();";
        view.evaluateJavascript(js, null);
    }

    private class NativeShareBridge {
        @JavascriptInterface
        public void share(String mediaUrl, String title) {
            // Validate URL before starting native network access.
            final Uri uri;
            try {
                uri = Uri.parse(mediaUrl);
                if (!"https".equalsIgnoreCase(uri.getScheme())
                        || uri.getHost() == null
                        || !uri.getHost().equalsIgnoreCase(ALLOWED_HOST)) {
                    showToast("Sharing is allowed only for Marina Publications media.");
                    return;
                }
            } catch (Exception ex) {
                showToast("Invalid media address.");
                return;
            }

            showToast("Preparing image for sharing…");
            shareExecutor.execute(() -> downloadAndShare(uri.toString(), title));
        }
    }

    private void downloadAndShare(String mediaUrl, String title) {
        File output = null;
        HttpURLConnection connection = null;
        try {
            URL url = new URL(mediaUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(45000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", System.getProperty("http.agent", "Mozilla/5.0 MarinaFestivalAndroid/1.0"));
            connection.setRequestProperty("Accept", "image/*,video/*,*/*");
            connection.setRequestProperty("Referer", SITE_URL);

            String cookies = CookieManager.getInstance().getCookie(mediaUrl);
            if (cookies != null && !cookies.isEmpty()) {
                connection.setRequestProperty("Cookie", cookies);
            }

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new Exception("Server returned HTTP " + status);
            }

            String finalHost = Uri.parse(connection.getURL().toString()).getHost();
            if (finalHost == null || !finalHost.equalsIgnoreCase(ALLOWED_HOST)) {
                throw new Exception("Unexpected media host");
            }

            String contentType = connection.getContentType();
            if (contentType == null) contentType = "";
            contentType = contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
            if (contentType.equals("text/html") || contentType.equals("application/json")) {
                throw new Exception("The media server returned a webpage instead of the file. Please log in again and retry.");
            }

            String path = Uri.parse(mediaUrl).getLastPathSegment();
            String ext = extensionFrom(path);
            String mime = mimeFromExtension(ext, contentType);
            if (!(mime.startsWith("image/") || mime.startsWith("video/"))) {
                throw new Exception("This file is not an image or video.");
            }

            File shareDir = new File(getCacheDir(), "shared_media");
            if (!shareDir.exists() && !shareDir.mkdirs()) throw new Exception("Cannot create cache folder");
            String safeName = "marina_" + System.currentTimeMillis() + "." + ext;
            output = new File(shareDir, safeName);

            long total = 0;
            byte[] buffer = new byte[16 * 1024];
            try (InputStream in = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream out = new FileOutputStream(output)) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > 150L * 1024L * 1024L) {
                        throw new Exception("Media file is larger than 150 MB.");
                    }
                    out.write(buffer, 0, read);
                }
            }

            if (!output.exists() || output.length() == 0) throw new Exception("Downloaded media is empty");

            File finalOutput = output;
            String finalMime = mime;
            mainHandler.post(() -> launchShare(finalOutput, finalMime, title));
        } catch (Exception e) {
            if (output != null && output.exists()) output.delete();
            String message = e.getMessage() == null ? "Could not prepare media." : e.getMessage();
            showToast(message);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String extensionFrom(String path) {
        if (path == null) return "jpg";
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) return "jpg";
        String ext = path.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ext.matches("[a-z0-9]{2,5}")) return "jpg";
        return ext;
    }

    private String mimeFromExtension(String ext, String serverType) {
        if (serverType.startsWith("image/") || serverType.startsWith("video/")) return serverType;
        switch (ext) {
            case "jpg": case "jpeg": return "image/jpeg";
            case "png": return "image/png";
            case "webp": return "image/webp";
            case "gif": return "image/gif";
            case "mp4": case "m4v": return "video/mp4";
            case "webm": return "video/webm";
            case "mov": return "video/quicktime";
            default: return "application/octet-stream";
        }
    }

    private void launchShare(File file, String mime, String title) {
        try {
            Uri contentUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    file
            );
            Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.setType(mime);
            sendIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            sendIntent.putExtra(Intent.EXTRA_SUBJECT, title);
            sendIntent.putExtra(Intent.EXTRA_TEXT, title);
            sendIntent.setClipData(ClipData.newUri(getContentResolver(), "Festival media", contentUri));
            sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Intent chooser = Intent.createChooser(sendIntent, "Share festival media");
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(chooser);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open Android sharing.", Toast.LENGTH_LONG).show();
        }
    }

    private void showToast(String message) {
        mainHandler.post(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST && filePathCallback != null) {
            Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (filePathCallback != null) filePathCallback.onReceiveValue(null);
        if (webView != null) {
            webView.removeJavascriptInterface("MarinaNativeShare");
            webView.destroy();
        }
        shareExecutor.shutdownNow();
        super.onDestroy();
    }
}
