package com.github.catvod.spider;

import android.content.Context;
import android.net.Uri;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;
import com.github.catvod.utils.WebViewHelper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KuaiKaw extends Spider {

    private static final String siteUrl = "https://www.kuaikaw.cn";
    private Context context;
    private boolean isSpaSite = false;
    private boolean detected = false;

    @Override
    public void init(Context context, String extend) {
        this.context = context;
        WebViewHelper.getInstance().preWarm(context);
    }

    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", Util.CHROME);
        header.put("Referer", siteUrl);
        return header;
    }

    private boolean isSpaSite(String html) {
        Document doc = Jsoup.parse(html);
        
        Elements items = doc.select(".video-list .item, .vod-list .item, .video-item, .drama-item");
        if (!items.isEmpty()) return false;
        
        if (html.contains("id=\"app\"") && html.contains("chunk")) return true;
        if (html.contains("__NEXT_DATA__")) return true;
        if (html.contains("window.__INITIAL_STATE__")) return true;
        if (html.contains("ng-version")) return true;
        if (html.contains("data-v-") && items.isEmpty()) return true;
        
        String bodyText = doc.select("body").text().trim();
        if (bodyText.length() < 100 && html.contains("<script")) return true;
        
        return false;
    }

    private String getHtml(String url) {
        if (!detected) {
            String html = OkHttp.string(url, getHeader());
            isSpaSite = isSpaSite(html);
            detected = true;
            if (!isSpaSite) return html;
        }
        
        if (isSpaSite) {
            String html = WebViewHelper.getInstance().getHtml(context, url, 15000);
            if (!html.isEmpty()) return html;
        }
        
        return OkHttp.string(url, getHeader());
    }

    @Override
    public String homeContent(boolean filter) {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("1", "全部"));
        classes.add(new Class("2", "都市"));
        classes.add(new Class("3", "古装"));
        classes.add(new Class("4", "言情"));
        classes.add(new Class("5", "悬疑"));
        classes.add(new Class("6", "喜剧"));

        List<Vod> list = new ArrayList<>();
        Document doc = Jsoup.parse(getHtml(siteUrl));
        
        Elements items = doc.select(".drama-item, .video-item, .item, a[href*=/detail/], a[href*=/video/]");
        
        for (Element item : items) {
            Element link = item.tagName().equals("a") ? item : item.selectFirst("a");
            if (link == null) continue;
            
            String vodId = link.attr("href");
            String vodName = link.attr("title");
            if (vodName == null || vodName.isEmpty()) {
                vodName = link.text();
            }
            
            Element img = item.selectFirst("img");
            String vodPic = "";
            if (img != null) {
                vodPic = img.attr("data-src");
                if (vodPic.isEmpty()) vodPic = img.attr("data-original");
                if (vodPic.isEmpty()) vodPic = img.attr("src");
                if (!vodPic.isEmpty() && !vodPic.startsWith("http")) {
                    vodPic = siteUrl + (vodPic.startsWith("/") ? "" : "/") + vodPic;
                }
            }
            
            String vodRemarks = "";
            Element remarksEl = item.selectFirst(".remarks, .status, .tag");
            if (remarksEl != null) {
                vodRemarks = remarksEl.text();
            }
            
            if (vodId != null && !vodId.isEmpty() && vodName != null && !vodName.isEmpty()) {
                list.add(new Vod(vodId, vodName, vodPic, vodRemarks));
            }
            
            if (list.size() >= 20) break;
        }

        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        List<Vod> list = new ArrayList<>();
        
        String cateUrl = siteUrl;
        if (!"1".equals(tid)) {
            cateUrl = siteUrl + "/category/" + tid;
        }
        if (!"1".equals(pg)) {
            cateUrl = cateUrl + "?page=" + pg;
        }
        
        Document doc = Jsoup.parse(getHtml(cateUrl));
        
        Elements items = doc.select(".drama-item, .video-item, .item, a[href*=/detail/], a[href*=/video/]");
        
        for (Element item : items) {
            Element link = item.tagName().equals("a") ? item : item.selectFirst("a");
            if (link == null) continue;
            
            String vodId = link.attr("href");
            String vodName = link.attr("title");
            if (vodName == null || vodName.isEmpty()) {
                vodName = link.text();
            }
            
            Element img = item.selectFirst("img");
            String vodPic = "";
            if (img != null) {
                vodPic = img.attr("data-src");
                if (vodPic.isEmpty()) vodPic = img.attr("data-original");
                if (vodPic.isEmpty()) vodPic = img.attr("src");
                if (!vodPic.isEmpty() && !vodPic.startsWith("http")) {
                    vodPic = siteUrl + (vodPic.startsWith("/") ? "" : "/") + vodPic;
                }
            }
            
            String vodRemarks = "";
            Element remarksEl = item.selectFirst(".remarks, .status, .tag");
            if (remarksEl != null) {
                vodRemarks = remarksEl.text();
            }
            
            if (vodId != null && !vodId.isEmpty() && vodName != null && !vodName.isEmpty()) {
                list.add(new Vod(vodId, vodName, vodPic, vodRemarks));
            }
        }

        return Result.string(list);
    }

    @Override
    public String detailContent(List<String> ids) {
        String detailUrl = ids.get(0);
        if (!detailUrl.startsWith("http")) {
            detailUrl = siteUrl + (detailUrl.startsWith("/") ? "" : "/") + detailUrl;
        }
        
        Document doc = Jsoup.parse(getHtml(detailUrl));
        
        Vod vod = new Vod();
        vod.setVodId(ids.get(0));
        
        Element titleEl = doc.selectFirst(".title, .video-title, h1, .drama-title");
        if (titleEl != null) {
            vod.setVodName(titleEl.text());
        }
        
        Element picEl = doc.selectFirst(".poster img, .video-pic img, img.cover, img");
        if (picEl != null) {
            String vodPic = picEl.attr("data-src");
            if (vodPic.isEmpty()) vodPic = picEl.attr("data-original");
            if (vodPic.isEmpty()) vodPic = picEl.attr("src");
            if (!vodPic.isEmpty() && !vodPic.startsWith("http")) {
                vodPic = siteUrl + (vodPic.startsWith("/") ? "" : "/") + vodPic;
            }
            vod.setVodPic(vodPic);
        }
        
        Element descEl = doc.selectFirst(".desc, .content, .intro, .description");
        if (descEl != null) {
            vod.setVodContent(descEl.text());
        }
        
        StringBuilder playFrom = new StringBuilder();
        StringBuilder playUrl = new StringBuilder();
        
        Elements episodes = doc.select(".episode-list a, .play-list a, .episodes a, a[href*=/play/]");
        
        if (!episodes.isEmpty()) {
            playFrom.append("默认");
            for (int i = 0; i < episodes.size(); i++) {
                Element ep = episodes.get(i);
                String epName = ep.text();
                if (epName == null || epName.isEmpty()) {
                    epName = "第" + (i + 1) + "集";
                }
                String epUrl = ep.attr("href");
                playUrl.append(epName).append("$").append(epUrl);
                if (i < episodes.size() - 1) {
                    playUrl.append("#");
                }
            }
        }
        
        vod.setVodPlayFrom(playFrom.toString());
        vod.setVodPlayUrl(playUrl.toString());
        
        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) {
        List<Vod> list = new ArrayList<>();
        
        try {
            String searchUrl = siteUrl + "/search?wd=" + Uri.encode(key);
            Document doc = Jsoup.parse(getHtml(searchUrl));
            
            Elements items = doc.select(".search-item, .drama-item, .video-item, .item, a[href*=/detail/]");
            
            for (Element item : items) {
                Element link = item.tagName().equals("a") ? item : item.selectFirst("a");
                if (link == null) continue;
                
                String vodId = link.attr("href");
                String vodName = link.attr("title");
                if (vodName == null || vodName.isEmpty()) {
                    vodName = link.text();
                }
                
                Element img = item.selectFirst("img");
                String vodPic = "";
                if (img != null) {
                    vodPic = img.attr("data-src");
                    if (vodPic.isEmpty()) vodPic = img.attr("data-original");
                    if (vodPic.isEmpty()) vodPic = img.attr("src");
                    if (!vodPic.isEmpty() && !vodPic.startsWith("http")) {
                        vodPic = siteUrl + (vodPic.startsWith("/") ? "" : "/") + vodPic;
                    }
                }
                
                String vodRemarks = "";
                Element remarksEl = item.selectFirst(".remarks, .status, .tag");
                if (remarksEl != null) {
                    vodRemarks = remarksEl.text();
                }
                
                if (vodId != null && !vodId.isEmpty() && vodName != null && !vodName.isEmpty()) {
                    list.add(new Vod(vodId, vodName, vodPic, vodRemarks));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        String playUrl = id;
        if (!playUrl.startsWith("http")) {
            playUrl = siteUrl + (playUrl.startsWith("/") ? "" : "/") + playUrl;
        }
        
        WebViewHelper.VideoResult result = WebViewHelper.getInstance()
            .getVideoSource(context, playUrl, 20000);
        
        if (result.hasVideo()) {
            return Result.get()
                .url(result.getBestVideo())
                .header(getHeader())
                .string();
        }
        
        String html = getHtml(playUrl);
        String videoUrl = matchVideoUrl(html);
        if (!videoUrl.isEmpty()) {
            return Result.get().url(videoUrl).header(getHeader()).string();
        }
        
        return Result.get().parse().url(playUrl).string();
    }

    private String matchVideoUrl(String html) {
        String[] patterns = {
            "\"(https?://[^\"]+\\.m3u8[^\"]*)\"",
            "'(https?://[^']+\\.m3u8[^']*)'",
            "\"(https?://[^\"]+\\.mp4[^\"]*)\"",
            "'(https?://[^']+\\.mp4[^']*)'",
            "url[\"']?\\s*[:=]\\s*[\"']([^\"']+(?:\\.m3u8|\\.mp4)[^\"']*)[\"']",
            "src[\"']?\\s*[:=]\\s*[\"']([^\"']+(?:\\.m3u8|\\.mp4)[^\"']*)[\"']"
        };
        
        for (String pattern : patterns) {
            Matcher matcher = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(html);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "";
    }

    @Override
    public void destroy() {
        WebViewHelper.getInstance().destroyPool();
    }
}
