package com.github.catvod.spider;

import android.content.Context;
import android.net.Uri;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

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

    private static String siteUrl = "https://www.kuaikaw.cn";

    @Override
    public void init(Context context, String extend) {
        if (!extend.isEmpty()) siteUrl = extend;
    }

    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", Util.CHROME);
        header.put("Referer", siteUrl);
        return header;
    }

    @Override
    public String homeContent(boolean filter) {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("1", "热门推荐"));
        classes.add(new Class("2", "最新上线"));
        classes.add(new Class("3", "古装仙侠"));
        classes.add(new Class("4", "都市情感"));
        classes.add(new Class("5", "豪门恩怨"));
        classes.add(new Class("6", "青春甜宠"));

        List<Vod> list = new ArrayList<>();
        Document doc = Jsoup.parse(OkHttp.string(siteUrl, getHeader()));
        
        Elements items = doc.select(".video-list .item, .vod-list .item, .short-video .item, .recommend-list li, .video-item");
        if (items.isEmpty()) items = doc.select("li[class*=video], div[class*=video], a[class*=video]");
        if (items.isEmpty()) items = doc.select(".list-item, .item-box, .card-item");
        
        for (Element item : items) {
            String vodId = "";
            String vodName = "";
            String vodPic = "";
            String vodRemarks = "";
            
            Element link = item.selectFirst("a");
            if (link != null) {
                vodId = link.attr("href");
                vodName = link.attr("title");
                if (vodName.isEmpty()) vodName = link.text();
            }
            
            Element img = item.selectFirst("img");
            if (img != null) {
                vodPic = img.attr("data-src");
                if (vodPic.isEmpty()) vodPic = img.attr("data-original");
                if (vodPic.isEmpty()) vodPic = img.attr("src");
                if (!vodPic.isEmpty() && !vodPic.startsWith("http")) vodPic = siteUrl + vodPic;
            }
            
            Element remark = item.selectFirst(".remark, .note, .update, .pic-text");
            if (remark != null) vodRemarks = remark.text();
            
            if (!vodId.isEmpty() && !vodName.isEmpty()) {
                list.add(new Vod(vodId, vodName, vodPic, vodRemarks));
            }
        }

        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        List<Vod> list = new ArrayList<>();
        String cateUrl = siteUrl + "/category/" + tid + "-" + pg + ".html";
        Document doc = Jsoup.parse(OkHttp.string(cateUrl, getHeader()));
        
        Elements items = doc.select(".video-list .item, .vod-list .item, .video-item");
        if (items.isEmpty()) items = doc.select("li[class*=video], div[class*=video]");
        
        for (Element item : items) {
            String vodId = "";
            String vodName = "";
            String vodPic = "";
            String vodRemarks = "";
            
            Element link = item.selectFirst("a");
            if (link != null) {
                vodId = link.attr("href");
                vodName = link.attr("title");
                if (vodName.isEmpty()) vodName = link.text();
            }
            
            Element img = item.selectFirst("img");
            if (img != null) {
                vodPic = img.attr("data-src");
                if (vodPic.isEmpty()) vodPic = img.attr("data-original");
                if (vodPic.isEmpty()) vodPic = img.attr("src");
                if (!vodPic.isEmpty() && !vodPic.startsWith("http")) vodPic = siteUrl + vodPic;
            }
            
            Element remark = item.selectFirst(".remark, .note, .update, .pic-text");
            if (remark != null) vodRemarks = remark.text();
            
            if (!vodId.isEmpty() && !vodName.isEmpty()) {
                list.add(new Vod(vodId, vodName, vodPic, vodRemarks));
            }
        }
        return Result.string(list);
    }

    @Override
    public String detailContent(List<String> ids) {
        String detailUrl = ids.get(0);
        if (!detailUrl.startsWith("http")) detailUrl = siteUrl + detailUrl;
        
        Document doc = Jsoup.parse(OkHttp.string(detailUrl, getHeader()));
        
        String vodName = doc.selectFirst(".video-title, .title, h1").text();
        String vodPic = "";
        Element picEl = doc.selectFirst(".video-pic img, .poster img, .cover img");
        if (picEl != null) {
            vodPic = picEl.attr("data-src");
            if (vodPic.isEmpty()) vodPic = picEl.attr("data-original");
            if (vodPic.isEmpty()) vodPic = picEl.attr("src");
            if (!vodPic.isEmpty() && !vodPic.startsWith("http")) vodPic = siteUrl + vodPic;
        }
        String vodContent = doc.selectFirst(".video-content, .desc, .intro, .summary").text();
        
        StringBuilder playFrom = new StringBuilder();
        StringBuilder playUrl = new StringBuilder();
        
        Elements playLists = doc.select(".play-list, .episode-list, .video-list");
        if (playLists.isEmpty()) playLists = doc.select("[class*=play], [class*=episode]");
        
        if (!playLists.isEmpty()) {
            playFrom.append("默认");
            Elements episodes = playLists.first().select("a");
            for (int i = 0; i < episodes.size(); i++) {
                Element ep = episodes.get(i);
                String epName = ep.text();
                String epUrl = ep.attr("href");
                playUrl.append(epName).append("$").append(epUrl);
                if (i < episodes.size() - 1) playUrl.append("#");
            }
        }
        
        Vod vod = new Vod();
        vod.setVodId(ids.get(0));
        vod.setVodName(vodName);
        vod.setVodPic(vodPic);
        vod.setVodContent(vodContent);
        vod.setVodPlayFrom(playFrom.toString());
        vod.setVodPlayUrl(playUrl.toString());
        
        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) {
        String searchUrl = siteUrl + "/search/" + Uri.encode(key) + ".html";
        Document doc = Jsoup.parse(OkHttp.string(searchUrl, getHeader()));
        
        List<Vod> list = new ArrayList<>();
        Elements items = doc.select(".search-list .item, .video-list .item, .search-item");
        if (items.isEmpty()) items = doc.select("li[class*=search], div[class*=result]");
        
        for (Element item : items) {
            String vodId = "";
            String vodName = "";
            String vodPic = "";
            String vodRemarks = "";
            
            Element link = item.selectFirst("a");
            if (link != null) {
                vodId = link.attr("href");
                vodName = link.attr("title");
                if (vodName.isEmpty()) vodName = link.text();
            }
            
            Element img = item.selectFirst("img");
            if (img != null) {
                vodPic = img.attr("data-src");
                if (vodPic.isEmpty()) vodPic = img.attr("data-original");
                if (vodPic.isEmpty()) vodPic = img.attr("src");
                if (!vodPic.isEmpty() && !vodPic.startsWith("http")) vodPic = siteUrl + vodPic;
            }
            
            Element remark = item.selectFirst(".remark, .note, .update");
            if (remark != null) vodRemarks = remark.text();
            
            if (!vodId.isEmpty() && !vodName.isEmpty()) {
                list.add(new Vod(vodId, vodName, vodPic, vodRemarks));
            }
        }
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        String playUrl = id;
        if (!playUrl.startsWith("http")) playUrl = siteUrl + id;
        
        String html = OkHttp.string(playUrl, getHeader());
        Document doc = Jsoup.parse(html);
        
        Element videoEl = doc.selectFirst("video source, video, iframe");
        if (videoEl != null) {
            String src = videoEl.attr("src");
            if (!src.isEmpty()) {
                if (!src.startsWith("http")) src = siteUrl + src;
                return Result.get().url(src).header(getHeader()).string();
            }
        }
        
        String videoUrl = matchVideoUrl(html);
        if (!videoUrl.isEmpty()) {
            return Result.get().url(videoUrl).header(getHeader()).string();
        }
        
        return Result.get().parse().url(playUrl).string();
    }
    
    private String matchVideoUrl(String html) {
        String[] patterns = {
            "url[\"']?\\s*[:=]\\s*[\"']([^\"']+\\.m3u8[^\"']*)[\"']",
            "src[\"']?\\s*[:=]\\s*[\"']([^\"']+\\.m3u8[^\"']*)[\"']",
            "\"(https?://[^\"]+\\.m3u8[^\"]*)\"",
            "'(https?://[^']+\\.m3u8[^']*)'",
            "(https?://[^\\s\"']+\\.m3u8[^\\s\"']*)"
        };
        
        for (String pattern : patterns) {
            Matcher matcher = Pattern.compile(pattern).matcher(html);
            if (matcher.find()) return matcher.group(1);
        }
        return "";
    }
}
