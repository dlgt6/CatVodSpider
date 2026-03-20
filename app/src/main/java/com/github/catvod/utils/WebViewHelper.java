package com.github.catvod.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class WebViewHelper {

    private static WebViewHelper instance;
    private final Handler mainHandler;
    private WebView webView;
    private boolean isInitialized = false;

    private WebViewHelper() {
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public static synchronized WebViewHelper getInstance() {
        if (instance == null) {
            instance = new WebViewHelper();
        }
        return instance;
    }

    @SuppressLint("SetJavaScriptEnabled")
    public void init(Context context) {
        if (isInitialized) return;
        
        mainHandler.post(() -> {
            try {
                webView = new WebView(context.getApplicationContext());
                WebSettings settings = webView.getSettings();
                settings.setJavaScriptEnabled(true);
                settings.setDomStorageEnabled(true);
                settings.setDatabaseEnabled(true);
                settings.setCacheMode(WebSettings.LOAD_DEFAULT);
                settings.setUserAgentString(Util.CHROME);
                settings.setLoadWithOverviewMode(true);
                settings.setUseWideViewPort(true);
                settings.setSupportZoom(true);
                settings.setBuiltInZoomControls(true);
                settings.setDisplayZoomControls(false);
                
                CookieManager cookieManager = CookieManager.getInstance();
                cookieManager.setAcceptCookie(true);
                cookieManager.setAcceptThirdPartyCookies(webView, true);
                
                isInitialized = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public String getHtml(String url) {
        return getHtml(url, 10000);
    }

    public String getHtml(String url, long timeoutMs) {
        if (!isInitialized) {
            return "";
        }

        AtomicReference<String> result = new AtomicReference<>("");
        CountDownLatch latch = new CountDownLatch(1);

        mainHandler.post(() -> {
            if (webView == null) {
                latch.countDown();
                return;
            }

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    view.evaluateJavascript(
                        "(function() { return document.documentElement.outerHTML; })();",
                        html -> {
                            if (html != null && html.length() > 2) {
                                html = html.substring(1, html.length() - 1);
                                html = html.replace("\\u003C", "<")
                                          .replace("\\u003E", ">")
                                          .replace("\\u002F", "/")
                                          .replace("\\\"", "\"")
                                          .replace("\\n", "\n")
                                          .replace("\\t", "\t");
                            }
                            result.set(html != null ? html : "");
                            latch.countDown();
                        }
                    );
                }
            });

            webView.loadUrl(url);
        });

        try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return result.get();
    }

    public String getHtmlWithJs(String url, String jsCode, long timeoutMs) {
        if (!isInitialized) {
            return "";
        }

        AtomicReference<String> result = new AtomicReference<>("");
        CountDownLatch latch = new CountDownLatch(1);

        mainHandler.post(() -> {
            if (webView == null) {
                latch.countDown();
                return;
            }

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    view.evaluateJavascript(jsCode, html -> {
                        if (html != null && html.length() > 2) {
                            html = html.substring(1, html.length() - 1);
                            html = html.replace("\\u003C", "<")
                                      .replace("\\u003E", ">")
                                      .replace("\\u002F", "/")
                                      .replace("\\\"", "\"")
                                      .replace("\\n", "\n")
                                      .replace("\\t", "\t");
                        }
                        result.set(html != null ? html : "");
                        latch.countDown();
                    });
                }
            });

            webView.loadUrl(url);
        });

        try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return result.get();
    }

    public void executeJs(String jsCode) {
        if (!isInitialized || webView == null) return;
        
        mainHandler.post(() -> {
            if (webView != null) {
                webView.evaluateJavascript(jsCode, null);
            }
        });
    }

    public void loadUrl(String url) {
        if (!isInitialized || webView == null) return;
        
        mainHandler.post(() -> {
            if (webView != null) {
                webView.loadUrl(url);
            }
        });
    }

    public void clearCache() {
        if (!isInitialized || webView == null) return;
        
        mainHandler.post(() -> {
            if (webView != null) {
                webView.clearCache(true);
                webView.clearHistory();
                CookieManager.getInstance().removeAllCookies(null);
            }
        });
    }

    public void destroy() {
        if (!isInitialized || webView == null) return;
        
        mainHandler.post(() -> {
            if (webView != null) {
                webView.stopLoading();
                webView.setWebViewClient(null);
                webView.destroy();
                webView = null;
                isInitialized = false;
            }
        });
    }
}
