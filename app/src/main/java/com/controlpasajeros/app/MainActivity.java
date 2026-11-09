package com.controlpasajeros.app;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private WebView webView;
    private FrameLayout root;
    private WebView printWebView;
    private ValueCallback<Uri[]> fileChooserCallback;
    private static final int FILE_CHOOSER_REQUEST = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(android.graphics.Color.rgb(8, 58, 125));
        getWindow().setNavigationBarColor(android.graphics.Color.rgb(8, 58, 125));

        root = new FrameLayout(this);
        webView = new WebView(this);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowContentAccess(true);
        webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        webView.setWebViewClient(new WebViewClient() {
            private boolean handleExternal(Uri uri) {
                if (uri == null || uri.getScheme() == null) return false;
                String scheme = uri.getScheme().toLowerCase();
                String host = uri.getHost() != null ? uri.getHost().toLowerCase() : "";
                if (scheme.equals("tel") || scheme.equals("sms") || scheme.equals("smsto")
                        || scheme.equals("mailto") || host.equals("wa.me")
                        || host.equals("api.whatsapp.com") || host.endsWith(".wa.me")) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "No hay aplicación para abrir ese enlace.", Toast.LENGTH_LONG).show();
                    }
                    return true;
                }
                return false;
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleExternal(request.getUrl());
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleExternal(Uri.parse(url));
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
                fileChooserCallback = filePathCallback;
                try {
                    startActivityForResult(fileChooserParams.createIntent(), FILE_CHOOSER_REQUEST);
                } catch (Exception e) {
                    fileChooserCallback = null;
                    Toast.makeText(MainActivity.this, "No se pudo abrir el selector de archivos.", Toast.LENGTH_LONG).show();
                    return false;
                }
                return true;
            }
        });
        webView.addJavascriptInterface(new AndroidBridge(this), "AndroidBridge");
        root.addView(webView);
        setContentView(root);
        webView.loadUrl("file:///android_asset/index.html");
    }

    private class AndroidBridge {
        private final Context context;
        AndroidBridge(Context context) { this.context = context; }

        private void toast(final String msg) {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
        }

        @JavascriptInterface
        public void shareText(String title, String text) {
            runOnUiThread(() -> {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_SUBJECT, title);
                send.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(send, "Compartir manifiesto"));
            });
        }

        @JavascriptInterface
        public void printHtml(String title, String html) {
            runOnUiThread(() -> {
                // Reutiliza un solo WebView de impresión: lo destruye antes de crear otro.
                if (printWebView != null) {
                    root.removeView(printWebView);
                    printWebView.destroy();
                    printWebView = null;
                }
                printWebView = new WebView(MainActivity.this);
                printWebView.setVisibility(View.INVISIBLE);
                // Debe estar añadido a la jerarquía de vistas para que
                // onPageFinished se dispare de forma fiable en todos los equipos.
                root.addView(printWebView, new FrameLayout.LayoutParams(1, 1));
                printWebView.getSettings().setJavaScriptEnabled(false);
                // El WebViewClient tiene que ponerse ANTES de cargar el contenido,
                // si no onPageFinished nunca se captura.
                printWebView.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                        printManager.print(title, view.createPrintDocumentAdapter(title),
                                new PrintAttributes.Builder().build());
                    }
                });
                printWebView.loadDataWithBaseURL("file:///android_asset/", html, "text/HTML", "UTF-8", null);
            });
        }

        @JavascriptInterface
        public void shareImageData(String title, String base64, String format) {
            runOnUiThread(() -> {
                final boolean jpg = "jpg".equalsIgnoreCase(format) || "jpeg".equalsIgnoreCase(format);
                final String safeTitle = (title == null || title.isEmpty()) ? "Reporte" : title;
                try {
                    // La imagen ya viene dibujada completa desde el canvas de JS
                    // (la captura por WebView cortaba el contenido: solo salia
                    // la cabecera). Aqui solo se decodifica, se guarda y se comparte.
                    String data = base64 == null ? "" : base64.trim();
                    if (data.startsWith("data:")) {
                        int comma = data.indexOf(',');
                        if (comma >= 0) data = data.substring(comma + 1);
                    }
                    byte[] bytes = Base64.decode(data, Base64.DEFAULT);
                    if (bytes.length == 0) throw new Exception("empty");
                    shareImageBytes(safeTitle, bytes, jpg);
                } catch (Exception e) {
                    toast("No se pudo generar la imagen del reporte.");
                }
            });
        }

        private void shareImageBytes(String title, byte[] bytes, boolean jpg) {
            try {
                String ext = jpg ? "jpg" : "png";
                String name = "reporte-" + new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date()) + "." + ext;
                String mime = jpg ? "image/jpeg" : "image/png";
                Uri uri;
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                    cv.put(MediaStore.Images.Media.MIME_TYPE, mime);
                    cv.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ControlPasajeros");
                    uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                    if (uri == null) throw new Exception("insert");
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                        if (os == null) throw new Exception("stream");
                        os.write(bytes);
                    }
                } else {
                    File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ControlPasajeros");
                    if (!dir.mkdirs() && !dir.isDirectory()) throw new Exception("mkdir");
                    File f = new File(dir, name);
                    try (FileOutputStream fos = new FileOutputStream(f)) {
                        fos.write(bytes);
                    }
                    uri = Uri.fromFile(f);
                }
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType(mime);
                send.putExtra(Intent.EXTRA_STREAM, uri);
                send.putExtra(Intent.EXTRA_SUBJECT, title);
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(send, "Compartir imagen del reporte"));
                toast("Imagen guardada en " + (Build.VERSION.SDK_INT >= 29 ? "Imágenes/ControlPasajeros." : "Pictures/ControlPasajeros."));
            } catch (Exception e) {
                toast("No se pudo generar la imagen del reporte.");
            }
        }

        @JavascriptInterface
        public void saveTextFile(String filename, String text) {
            saveFile(filename, text, "text/plain");
        }

        @JavascriptInterface
        public void saveFile(String filename, String text, String mime) {
            runOnUiThread(() -> {
                String name = (filename == null || filename.isEmpty()) ? "manifiesto.txt" : filename;
                try {
                    if (Build.VERSION.SDK_INT >= 29) {
                        // Android 10+: MediaStore, sin permisos adicionales.
                        ContentValues cv = new ContentValues();
                        cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                        cv.put(MediaStore.Downloads.MIME_TYPE, (mime == null || mime.isEmpty()) ? "text/plain" : mime);
                        cv.put(MediaStore.Downloads.RELATIVE_PATH, "Download/ControlPasajeros");
                        Uri uri = getContentResolver().insert(
                                MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                        if (uri == null) throw new Exception("insert");
                        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                            if (os == null) throw new Exception("stream");
                            os.write(text.getBytes(StandardCharsets.UTF_8));
                        }
                        toast("Archivo guardado en Descargas/ControlPasajeros.");
                    } else {
                        // Android 9 o inferior: carpeta pública (puede requerir permiso).
                        File dir = new File(Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_DOWNLOADS), "ControlPasajeros");
                        if (!dir.mkdirs() && !dir.isDirectory()) throw new Exception("mkdir");
                        File f = new File(dir, name);
                        try (FileOutputStream fos = new FileOutputStream(f)) {
                            fos.write(text.getBytes(StandardCharsets.UTF_8));
                        }
                        sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(f)));
                        toast("Archivo guardado en Descargas/ControlPasajeros.");
                    }
                } catch (Exception e) {
                    // Sin permiso de escritura: carpeta privada de la app como alternativa.
                    try {
                        File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                                "ControlPasajeros");
                        if (dir != null) dir.mkdirs();
                        File f = new File(dir, name);
                        try (FileOutputStream fos = new FileOutputStream(f)) {
                            fos.write(text.getBytes(StandardCharsets.UTF_8));
                        }
                        toast("Guardado en la carpeta privada de la app.");
                    } catch (Exception e2) {
                        toast("No se pudo guardar el archivo.");
                    }
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST && fileChooserCallback != null) {
            Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            fileChooserCallback.onReceiveValue(results);
            fileChooserCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (printWebView != null) {
            root.removeView(printWebView);
            printWebView.destroy();
            printWebView = null;
        }
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
