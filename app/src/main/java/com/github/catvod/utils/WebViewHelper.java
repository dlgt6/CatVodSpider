package com.github.catvod.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.RenderProcessGoneDetail;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class WebViewHelper {

    private static volatile WebViewHelper instance;
    private final Handler mainHandler;
    private String customUserAgent;
    private boolean ignoreSslErrors = true;
    private long defaultDelay = 500;
    private int retryCount = 1;
    
    private static final int POOL_SIZE = 2;
    private static final long POOL_IDLE_TIMEOUT = 60000;
    private final ConcurrentLinkedQueue<WebViewPoolEntry> webViewPool = new ConcurrentLinkedQueue<>();
    private final AtomicInteger poolCount = new AtomicInteger(0);

    private static class WebViewPoolEntry {
        WebView webView;
        long lastUsedTime;
        
        WebViewPoolEntry(WebView webView) {
            this.webView = webView;
            this.lastUsedTime = System.currentTimeMillis();
        }
    }

    private WebViewHelper() {
        mainHandler = new Handler(Looper.getMainLooper());
        startPoolCleaner();
    }

    public static WebViewHelper getInstance() {
        if (instance == null) {
            synchronized (WebViewHelper.class) {
                if (instance == null) {
                    instance = new WebViewHelper();
                }
            }
        }
        return instance;
    }

    public void setCustomUserAgent(String userAgent) {
        this.customUserAgent = userAgent;
    }

    public void setIgnoreSslErrors(boolean ignore) {
        this.ignoreSslErrors = ignore;
    }

    public void setDefaultDelay(long delayMs) {
        this.defaultDelay = delayMs;
    }

    public void setRetryCount(int count) {
        this.retryCount = Math.max(1, count);
    }

    public void preWarm(Context context) {
        mainHandler.post(() -> {
            if (poolCount.get() < POOL_SIZE && webViewPool.isEmpty()) {
                try {
                    WebView wv = createWebView(context);
                    wv.loadUrl("about:blank");
                    webViewPool.offer(new WebViewPoolEntry(wv));
                    poolCount.incrementAndGet();
                } catch (Exception ignored) {}
            }
        });
    }

    private void startPoolCleaner() {
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                cleanIdleWebViews();
                mainHandler.postDelayed(this, 15000);
            }
        }, 15000);
    }

    private void cleanIdleWebViews() {
        long now = System.currentTimeMillis();
        webViewPool.removeIf(entry -> {
            if (now - entry.lastUsedTime > POOL_IDLE_TIMEOUT) {
                destroyWebView(entry.webView);
                poolCount.decrementAndGet();
                return true;
            }
            return false;
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView acquireWebView(Context context) {
        WebViewPoolEntry entry = webViewPool.poll();
        if (entry != null) {
            WebView wv = entry.webView;
            try {
                wv.clearCache(true);
                wv.clearFormData();
                wv.clearHistory();
            } catch (Exception ignored) {}
            return wv;
        }
        
        if (poolCount.get() < POOL_SIZE) {
            poolCount.incrementAndGet();
            return createWebView(context);
        }
        
        return createWebView(context);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView createWebView(Context context) {
        WebView wv = new WebView(context.getApplicationContext());
        WebSettings settings = wv.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString(customUserAgent != null ? customUserAgent : Util.CHROME);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setBlockNetworkImage(true);
        settings.setLoadsImagesAutomatically(false);
        settings.setMediaPlaybackRequiresUserGesture(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptCookie(true);
            cookieManager.setAcceptThirdPartyCookies(wv, true);
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            wv.setWebChromeClient(new WebChromeClient() {
                @Override
                public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                    poolCount.decrementAndGet();
                    destroyWebView(view);
                    return true;
                }
            });
        }
        
        return wv;
    }

    private void releaseWebView(WebView wv) {
        if (wv == null) return;
        
        try {
            wv.stopLoading();
            wv.loadUrl("about:blank");
            wv.clearHistory();
        } catch (Exception ignored) {}
        
        final boolean shouldPool = poolCount.get() <= POOL_SIZE;
        if (!shouldPool) {
            destroyWebView(wv);
            return;
        }
        
        mainHandler.postDelayed(() -> {
            try {
                webViewPool.offer(new WebViewPoolEntry(wv));
            } catch (Exception e) {
                destroyWebView(wv);
            }
        }, 200);
    }

    public String getHtml(Context context, String url) {
        return fetchHtml(context, url, 15000, null, null);
    }

    public String getHtml(Context context, String url, long timeoutMs) {
        return fetchHtml(context, url, timeoutMs, null, null);
    }

    public String getHtml(Context context, String url, long timeoutMs, Map<String, String> headers) {
        return fetchHtml(context, url, timeoutMs, headers, null);
    }

    public String getHtmlWithJs(Context context, String url, String jsCode, long timeoutMs) {
        return fetchHtml(context, url, timeoutMs, null, jsCode);
    }

    public String getHtmlWithJs(Context context, String url, String jsCode, long timeoutMs, Map<String, String> headers) {
        return fetchHtml(context, url, timeoutMs, headers, jsCode);
    }

    private String fetchHtml(Context context, String url, long timeoutMs, 
                             Map<String, String> headers, String jsCode) {
        
        for (int attempt = 0; attempt < retryCount; attempt++) {
            try {
                String result = doFetchHtml(context, url, timeoutMs, headers, jsCode);
                if (result != null && !result.isEmpty()) {
                    return result;
                }
            } catch (Exception ignored) {}
        }
        
        return "";
    }

    private String doFetchHtml(Context context, String url, long timeoutMs,
                               Map<String, String> headers, String jsCode) {
        
        AtomicReference<String> result = new AtomicReference<>("");
        AtomicBoolean finished = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<WebView> webViewRef = new AtomicReference<>();

        mainHandler.post(() -> {
            WebView wv = null;
            try {
                wv = acquireWebView(context);
                webViewRef.set(wv);

                final WebView finalWv = wv;
                wv.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String loadedUrl) {
                        if (finished.getAndSet(true)) return;
                        
                        mainHandler.postDelayed(() -> {
                            try {
                                String js = jsCode != null ? jsCode : 
                                    "JSON.stringify({html: document.documentElement.outerHTML});";
                                
                                finalWv.evaluateJavascript(js, value -> {
                                    result.set(parseJsResult(value));
                                    latch.countDown();
                                    releaseWebView(finalWv);
                                });
                            } catch (Exception e) {
                                latch.countDown();
                                releaseWebView(finalWv);
                            }
                        }, defaultDelay);
                    }

                    @Override
                    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                        if (request.isForMainFrame()) {
                            if (!finished.getAndSet(true)) {
                                latch.countDown();
                                releaseWebView(finalWv);
                            }
                        }
                    }

                    @Override
                    public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                        if (request.isForMainFrame()) {
                            if (!finished.getAndSet(true)) {
                                latch.countDown();
                                releaseWebView(finalWv);
                            }
                        }
                    }

                    @Override
                    public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
                        if (ignoreSslErrors) {
                            handler.proceed();
                        } else {
                            handler.cancel();
                            if (!finished.getAndSet(true)) {
                                latch.countDown();
                                releaseWebView(finalWv);
                            }
                        }
                    }
                });

                if (headers != null && !headers.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    wv.loadUrl(url, headers);
                } else {
                    wv.loadUrl(url);
                }
            } catch (Exception e) {
                if (!finished.getAndSet(true)) {
                    latch.countDown();
                }
                if (wv != null) {
                    destroyWebView(wv);
                }
            }
        });

        try {
            boolean completed = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            if (!completed) {
                finished.set(true);
                WebView wv = webViewRef.get();
                if (wv != null) {
                    mainHandler.post(() -> destroyWebView(wv));
                }
            }
        } catch (InterruptedException e) {
            finished.set(true);
            WebView wv = webViewRef.get();
            if (wv != null) {
                mainHandler.post(() -> destroyWebView(wv));
            }
        }

        return result.get();
    }

    public VideoResult getVideoSource(Context context, String url) {
        return getVideoSource(context, url, 20000, null);
    }

    public VideoResult getVideoSource(Context context, String url, long timeoutMs) {
        return getVideoSource(context, url, timeoutMs, null);
    }

    public VideoResult getVideoSource(Context context, String url, long timeoutMs, Map<String, String> headers) {
        AtomicReference<VideoResult> resultRef = new AtomicReference<>(new VideoResult());
        AtomicBoolean finished = new AtomicBoolean(false);
        AtomicBoolean timeout = new AtomicBoolean(false);
        AtomicReference<String> loadedUrlRef = new AtomicReference<>(url);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<WebView> webViewRef = new AtomicReference<>();
        Set<String> capturedUrls = new HashSet<>();

        mainHandler.post(() -> {
            WebView wv = null;
            try {
                wv = acquireWebView(context);
                webViewRef.set(wv);

                final WebView finalWv = wv;
                wv.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String loadedUrl) {
                        loadedUrlRef.set(loadedUrl);
                        mainHandler.postDelayed(() -> {
                            if (finished.getAndSet(true)) return;
                            
                            try {
                                String js = buildVideoDetectionJs();
                                finalWv.evaluateJavascript(js, value -> {
                                    VideoResult vr = parseVideoResult(value);
                                    vr.addCapturedUrls(capturedUrls);
                                    vr.loadedUrl = loadedUrl;
                                    resultRef.set(vr);
                                    latch.countDown();
                                    releaseWebView(finalWv);
                                });
                            } catch (Exception e) {
                                resultRef.set(new VideoResult());
                                latch.countDown();
                                releaseWebView(finalWv);
                            }
                        }, defaultDelay + 1000);
                    }

                    @Override
                    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                        String requestUrl = request.getUrl().toString();
                        
                        if (isVideoUrl(requestUrl)) {
                            synchronized (capturedUrls) {
                                capturedUrls.add(requestUrl);
                            }
                        }
                        
                        return super.shouldInterceptRequest(view, request);
                    }

                    @Override
                    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                        if (request.isForMainFrame()) {
                            if (!finished.getAndSet(true)) {
                                resultRef.set(new VideoResult());
                                latch.countDown();
                                releaseWebView(finalWv);
                            }
                        }
                    }

                    @Override
                    public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
                        if (ignoreSslErrors) {
                            handler.proceed();
                        } else {
                            handler.cancel();
                            if (!finished.getAndSet(true)) {
                                resultRef.set(new VideoResult());
                                latch.countDown();
                                releaseWebView(finalWv);
                            }
                        }
                    }
                });

                if (headers != null && !headers.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    wv.loadUrl(url, headers);
                } else {
                    wv.loadUrl(url);
                }
            } catch (Exception e) {
                if (!finished.getAndSet(true)) {
                    resultRef.set(new VideoResult());
                    latch.countDown();
                }
                if (wv != null) {
                    destroyWebView(wv);
                }
            }
        });

        try {
            boolean completed = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            if (!completed) {
                finished.set(true);
                timeout.set(true);
                WebView wv = webViewRef.get();
                if (wv != null) {
                    mainHandler.post(() -> destroyWebView(wv));
                }
            }
        } catch (InterruptedException e) {
            finished.set(true);
            timeout.set(true);
            WebView wv = webViewRef.get();
            if (wv != null) {
                mainHandler.post(() -> destroyWebView(wv));
            }
        }

        VideoResult result = resultRef.get();
        if (result == null) {
            result = new VideoResult();
        }
        result.timeout = timeout.get();
        result.loadedUrl = loadedUrlRef.get();
        return result;
    }

    private boolean isVideoUrl(String url) {
        if (url == null || url.length() < 30) return false;
        String lower = url.toLowerCase();
        
        if (lower.contains(".woff") || lower.contains(".ttf") || lower.contains(".eot") || lower.contains(".otf")) return false;
        if (lower.contains("width=") || lower.contains("height=") || lower.contains("size=")) return false;
        if (lower.contains("/avatar") || lower.contains("/thumb") || lower.contains("/icon") || lower.contains("/logo")) return false;
        if (lower.contains("/font") || lower.contains("/image") || lower.contains("/poster") || lower.contains("/cover")) return false;
        if (lower.contains("/api/") && !lower.contains("/play") && !lower.contains("/video")) return false;
        
        if (lower.endsWith(".m3u8") || lower.contains(".m3u8?") || lower.contains(".m3u8#")) return true;
        if (lower.endsWith(".mp4") || lower.contains(".mp4?") || lower.contains(".mp4#")) return true;
        if (lower.endsWith(".flv") || lower.contains(".flv?")) return true;
        if (lower.endsWith(".m4s")) return true;
        
        if (lower.endsWith(".ts")) {
            if (lower.contains(".js.ts") || lower.contains(".css.ts") || lower.contains(".vue.ts")) return false;
            if (lower.contains("width=") || lower.contains("height=") || lower.contains("size=")) return false;
            if (lower.contains("/font") || lower.contains("/image") || lower.contains("/thumb") || lower.contains("/poster")) return false;
            if (lower.contains("/hls/") || lower.contains("/dash/") || lower.contains("/live/") || 
                lower.contains("/vod/") || lower.contains("chunk") || lower.contains("segment") ||
                lower.contains("stream") || lower.contains("play")) {
                return true;
            }
            return false;
        }
        
        if (lower.contains("/hls/") && (lower.contains(".m3u8") || lower.contains(".ts"))) return true;
        if (lower.contains("/dash/") && lower.contains(".mpd")) return true;
        if (lower.contains("/live/") && (lower.contains(".m3u8") || lower.contains(".flv"))) return true;
        if (lower.contains("/vod/") && (lower.contains(".m3u8") || lower.contains(".mp4"))) return true;
        if (lower.contains("playlist.m3u8") || lower.contains("master.m3u8")) return true;
        if (lower.contains("index.m3u8") || lower.contains("stream.m3u8")) return true;
        
        return false;
    }

    private String buildVideoDetectionJs() {
        return "(function() {" +
            "  return new Promise(resolve => {" +
            "    let videos = [];" +
            "    let html = '';" +
            "    try { " +
            "      let fullHtml = document.documentElement.outerHTML;" +
            "      html = fullHtml.length > 50000 ? fullHtml.substring(0, 50000) : fullHtml;" +
            "    } catch(e) {}" +
            "    " +
            "    document.querySelectorAll('video source, video').forEach(v => {" +
            "      let src = v.src || v.currentSrc || v.getAttribute('data-src') || v.getAttribute('src');" +
            "      if (src) videos.push(src);" +
            "    });" +
            "    " +
            "    document.querySelectorAll('iframe').forEach(f => {" +
            "      let src = f.src || f.getAttribute('data-src');" +
            "      if (src && (src.includes('.m3u8') || src.includes('.mp4') || src.includes('play'))) videos.push(src);" +
            "    });" +
            "    " +
            "    if (window.player && window.player.src) videos.push(window.player.src);" +
            "    if (window.player && window.player.config && window.player.config.url) videos.push(window.player.config.url);" +
            "    if (window.player && window.player.option && window.player.option.url) videos.push(window.player.option.url);" +
            "    if (window.hls && window.hls.url) videos.push(window.hls.url);" +
            "    if (window.Hls && window.Hls.instances && window.Hls.instances[0] && window.Hls.instances[0].url) videos.push(window.Hls.instances[0].url);" +
            "    if (window.videoUrl) videos.push(window.videoUrl);" +
            "    if (window.video_url) videos.push(window.video_url);" +
            "    if (window.playUrl) videos.push(window.playUrl);" +
            "    if (window.play_url) videos.push(window.play_url);" +
            "    if (window.__PLAYER__ && window.__PLAYER__.url) videos.push(window.__PLAYER__.url);" +
            "    if (window.__playinfo__) videos.push(JSON.stringify(window.__playinfo__));" +
            "    if (window.__INITIAL_STATE__) videos.push(JSON.stringify(window.__INITIAL_STATE__));" +
            "    " +
            "    if (window.xgplayer) {" +
            "      if (window.xgplayer.getUrl) videos.push(window.xgplayer.getUrl());" +
            "      if (window.xgplayer.config && window.xgplayer.config.url) videos.push(window.xgplayer.config.url);" +
            "    }" +
            "    if (window.dplayer && window.dplayer.options && window.dplayer.options.video) videos.push(window.dplayer.options.video);" +
            "    if (window.CKplayer && window.CKplayer.object) videos.push(window.CKplayer.object);" +
            "    if (window.tcplayer && window.tcplayer.playUrl) videos.push(window.tcplayer.playUrl);" +
            "    if (window.dp && window.dp.options && window.dp.options.video) videos.push(window.dp.options.video);" +
            "    " +
            "    if (window.artplayer) {" +
            "      if (window.artplayer.url) videos.push(window.artplayer.url);" +
            "      if (window.artplayer.option && window.artplayer.option.url) videos.push(window.artplayer.option.url);" +
            "      try { if (window.artplayer.src) videos.push(window.artplayer.src); } catch(e) {}" +
            "    }" +
            "    if (window.videojs && window.videojs.players && Object.keys(window.videojs.players).length > 0) {" +
            "      try {" +
            "        let p = window.videojs.players[Object.keys(window.videojs.players)[0]];" +
            "        if (p && p.src) videos.push(p.src());" +
            "        if (p && p.currentSrc) videos.push(p.currentSrc());" +
            "      } catch(e) {}" +
            "    }" +
            "    if (window.p2p && window.p2p.url) videos.push(window.p2p.url);" +
            "    if (window.mpegts && window.mpegts.player && window.mpegts.player.url) videos.push(window.mpegts.player.url);" +
            "    if (window.flvjs && window.flvjs.players && window.flvjs.players.length > 0) {" +
            "      try { videos.push(window.flvjs.players[0]._mediaDataSource.url); } catch(e) {}" +
            "    }" +
            "    if (window.jwplayer) {" +
            "      try { videos.push(window.jwplayer().getPlaylistItem().file); } catch(e) {}" +
            "      try { videos.push(window.jwplayer().getConfig().file); } catch(e) {}" +
            "    }" +
            "    if (window.flowplayer && window.flowplayer.conf) videos.push(window.flowplayer.conf.clip && window.flowplayer.conf.clip.sources && window.flowplayer.conf.clip.sources[0] && window.flowplayer.conf.clip.sources[0].src);" +
            "    " +
            "    let scripts = document.querySelectorAll('script');" +
            "    scripts.forEach(s => {" +
            "      let text = s.textContent || s.innerHTML;" +
            "      let matches = text.match(/['\"](https?:\\/\\/[^'\"]+\\.(?:m3u8|mp4|flv)[^'\"]*)['\"]/gi);" +
            "      if (matches) {" +
            "        matches.forEach(m => {" +
            "          let url = m.replace(/['\"]/g, '');" +
            "          videos.push(url);" +
            "        });" +
            "      }" +
            "    });" +
            "    " +
            "    let unique = [...new Set(videos.filter(u => u && u.length > 10))];" +
            "    " +
            "    resolve(JSON.stringify({html: html, videos: unique}));" +
            "  });" +
            "})();";
    }

    private VideoResult parseVideoResult(String value) {
        VideoResult result = new VideoResult();
        if (value == null || value.isEmpty()) return result;
        
        try {
            if (value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            
            JSONObject json = new JSONObject(value);
            result.html = json.optString("html", "");
            
            JSONArray videos = json.optJSONArray("videos");
            if (videos != null) {
                for (int i = 0; i < videos.length(); i++) {
                    String url = videos.getString(i);
                    if (url != null && !url.isEmpty()) {
                        result.videoUrls.add(url);
                    }
                }
            }
        } catch (Exception e) {
            result.html = unescapeHtml(value);
        }
        
        return result;
    }

    public static class VideoResult {
        public String html = "";
        public List<String> videoUrls = new ArrayList<>();
        public boolean timeout = false;
        public String loadedUrl = "";
        
        public boolean hasVideo() {
            return !videoUrls.isEmpty();
        }
        
        public boolean isTimeout() {
            return timeout;
        }
        
        public String getFirstVideo() {
            return videoUrls.isEmpty() ? null : videoUrls.get(0);
        }
        
        public boolean hasM3u8() {
            for (String url : videoUrls) {
                if (url.contains(".m3u8")) return true;
            }
            return false;
        }
        
        public String getFirstM3u8() {
            for (String url : videoUrls) {
                if (url.contains(".m3u8")) return url;
            }
            return null;
        }
        
        public String getBestM3u8() {
            for (String url : videoUrls) {
                String lower = url.toLowerCase();
                if (lower.contains(".m3u8") && (lower.contains("master") || lower.contains("playlist"))) {
                    return url;
                }
            }
            return getFirstM3u8();
        }
        
        public String getBestVideo() {
            if (hasM3u8()) {
                return getBestM3u8();
            }
            List<String> mp4s = getMp4Urls();
            if (!mp4s.isEmpty()) {
                return mp4s.get(0);
            }
            return getFirstVideo();
        }
        
        public List<String> getM3u8Urls() {
            List<String> result = new ArrayList<>();
            for (String url : videoUrls) {
                if (url.contains(".m3u8")) {
                    result.add(url);
                }
            }
            Collections.sort(result, (a, b) -> {
                int scoreA = getM3u8Score(a);
                int scoreB = getM3u8Score(b);
                return scoreB - scoreA;
            });
            return result;
        }
        
        private int getM3u8Score(String url) {
            int score = 0;
            String lower = url.toLowerCase();
            if (lower.contains("master")) score += 100;
            if (lower.contains("playlist")) score += 90;
            if (lower.contains("index")) score += 80;
            if (lower.contains("1080")) score += 50;
            if (lower.contains("720")) score += 40;
            return score;
        }
        
        public List<String> getMp4Urls() {
            List<String> result = new ArrayList<>();
            for (String url : videoUrls) {
                if (url.contains(".mp4")) {
                    result.add(url);
                }
            }
            return result;
        }
        
        void addCapturedUrls(Set<String> captured) {
            for (String url : captured) {
                if (!videoUrls.contains(url)) {
                    videoUrls.add(url);
                }
            }
        }
    }

    public String waitForElement(Context context, String url, String cssSelector, long timeoutMs) {
        String jsCode = String.format(
            "JSON.stringify({html: (function() {" +
            "  return new Promise(resolve => {" +
            "    const check = () => {" +
            "      const el = document.querySelector('%s');" +
            "      if (el) {" +
            "        resolve(document.documentElement.outerHTML);" +
            "      } else {" +
            "        setTimeout(check, 200);" +
            "      }" +
            "    };" +
            "    check();" +
            "  });" +
            "})()});",
            cssSelector
        );
        return fetchHtml(context, url, timeoutMs, null, jsCode);
    }

    public String waitForVideo(Context context, String url, long timeoutMs) {
        String jsCode = 
            "JSON.stringify({html: (function() {" +
            "  return new Promise(resolve => {" +
            "    const check = () => {" +
            "      let video = document.querySelector('video');" +
            "      if (video && (video.src || video.currentSrc)) {" +
            "        resolve(document.documentElement.outerHTML);" +
            "      } else {" +
            "        setTimeout(check, 300);" +
            "      }" +
            "    };" +
            "    check();" +
            "  });" +
            "})()});";
        return fetchHtml(context, url, timeoutMs, null, jsCode);
    }

    public String scrollAndLoad(Context context, String url, int scrollTimes, long timeoutMs) {
        String jsCode = String.format(
            "JSON.stringify({html: (function() {" +
            "  return new Promise(resolve => {" +
            "    let count = 0;" +
            "    const scroll = () => {" +
            "      if (count < %d) {" +
            "        window.scrollTo(0, document.body.scrollHeight);" +
            "        count++;" +
            "        setTimeout(scroll, 500);" +
            "      } else {" +
            "        resolve(document.documentElement.outerHTML);" +
            "      }" +
            "    };" +
            "    scroll();" +
            "  });" +
            "})()});",
            scrollTimes
        );
        return fetchHtml(context, url, timeoutMs, null, jsCode);
    }

    public String waitForCondition(Context context, String url, String conditionJs, long timeoutMs) {
        String jsCode = String.format(
            "JSON.stringify({html: (function() {" +
            "  return new Promise(resolve => {" +
            "    const check = () => {" +
            "      if (%s) {" +
            "        resolve(document.documentElement.outerHTML);" +
            "      } else {" +
            "        setTimeout(check, 300);" +
            "      }" +
            "    };" +
            "    check();" +
            "  });" +
            "})()});",
            conditionJs
        );
        return fetchHtml(context, url, timeoutMs, null, jsCode);
    }

    private String parseJsResult(String value) {
        if (value == null || value.isEmpty()) return "";
        
        if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        
        try {
            JSONObject json = new JSONObject(value);
            if (json.has("html")) {
                return json.getString("html");
            }
        } catch (Exception ignored) {}
        
        return unescapeHtml(value);
    }

    private void destroyWebView(WebView wv) {
        if (wv == null) return;
        try {
            wv.stopLoading();
            wv.setWebViewClient(null);
            wv.setWebChromeClient(null);
            wv.loadUrl("about:blank");
            wv.clearCache(true);
            wv.clearHistory();
            wv.destroy();
        } catch (Exception ignored) {}
    }

    public void clearAllCookies() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().removeAllCookies(null);
        } else {
            CookieManager.getInstance().removeAllCookie();
        }
    }

    public void destroyPool() {
        WebViewPoolEntry entry;
        while ((entry = webViewPool.poll()) != null) {
            destroyWebView(entry.webView);
        }
        poolCount.set(0);
    }

    private String unescapeHtml(String html) {
        if (html == null || html.isEmpty()) return "";
        
        return html
            .replace("\\u003C", "<")
            .replace("\\u003E", ">")
            .replace("\\u002F", "/")
            .replace("\\u0026", "&")
            .replace("\\u003D", "=")
            .replace("\\u003F", "?")
            .replace("\\u0025", "%")
            .replace("\\\"", "\"")
            .replace("\\'", "'")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")
            .replace("\\/", "/");
    }
}
