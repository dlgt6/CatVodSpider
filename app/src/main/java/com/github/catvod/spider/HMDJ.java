package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 河马短剧爬虫
 */
public class HMDJ extends Spider {
    private final String siteUrl = "https://www.kuaikaw.cn";
    private final Map<String, String> cateManual = new HashMap<String, String>() {{
        put("甜宠", "462");
        put("古装仙侠", "1102");
        put("现代言情", "1145");
        put("青春", "1170");
        put("豪门恩怨", "585");
        put("逆袭", "417-464");
        put("重生", "439-465");
        put("系统", "1159");
        put("总裁", "1147");
        put("职场商战", "943");
    }};
    
    private final Map<String, String> headers = new HashMap<String, String>() {{
        put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0");
        put("Referer", siteUrl);
        put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8");
        put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
    }};

    @Override
    public void init(Context context) throws Exception {
        super.init(context);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        for (Map.Entry<String, String> entry : cateManual.entrySet()) {
            classes.add(new Class(entry.getValue(), entry.getKey()));
        }
        
        List<Vod> list = new ArrayList<>();
        try {
            String resultStr = homeVideoContent();
            JSONObject result = new JSONObject(resultStr);
            JSONArray array = result.optJSONArray("list");
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject vodObj = array.getJSONObject(i);
                    list.add(new Vod(
                        vodObj.optString("vod_id"),
                        vodObj.optString("vod_name"),
                        vodObj.optString("vod_pic"),
                        vodObj.optString("vod_remarks")
                    ));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return Result.string(classes, list);
    }

    @Override
    public String homeVideoContent() throws Exception {
        List<Vod> videos = new ArrayList<>();
        Map<String, String> response = fetch(siteUrl);
        if (response == null || !response.containsKey("body")) {
            JSONObject result = new JSONObject();
            result.put("list", new JSONArray());
            return result.toString();
        }
        
        String htmlContent = response.get("body");
        Pattern pattern = Pattern.compile("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(htmlContent);
        if (!matcher.find()) {
            JSONObject result = new JSONObject();
            result.put("list", new JSONArray());
            return result.toString();
        }
        
        JSONObject nextDataJson = new JSONObject(matcher.group(1));
        JSONObject pageProps = nextDataJson.optJSONObject("props").optJSONObject("pageProps");
        
        // 处理轮播图数据
        if (pageProps.has("bannerList")) {
            JSONArray bannerList = pageProps.optJSONArray("bannerList");
            for (int i = 0; i < bannerList.length(); i++) {
                JSONObject banner = bannerList.getJSONObject(i);
                if (banner.has("bookId")) {
                    Vod vod = new Vod();
                    vod.setVodId("/drama/" + banner.getString("bookId"));
                    vod.setVodName(banner.optString("bookName", ""));
                    vod.setVodPic(banner.optString("coverWap", ""));
                    vod.setVodRemarks((banner.optString("statusDesc", "") + " " + banner.optString("totalChapterNum", "") + "集").trim());
                    videos.add(vod);
                }
            }
        }
        
        // 处理SEO分类推荐
        if (pageProps.has("seoColumnVos")) {
            JSONArray seoColumnVos = pageProps.optJSONArray("seoColumnVos");
            for (int i = 0; i < seoColumnVos.length(); i++) {
                JSONObject column = seoColumnVos.getJSONObject(i);
                JSONArray bookInfos = column.optJSONArray("bookInfos");
                if (bookInfos != null) {
                    for (int j = 0; j < bookInfos.length(); j++) {
                        JSONObject book = bookInfos.getJSONObject(j);
                        if (book.has("bookId")) {
                            Vod vod = new Vod();
                            vod.setVodId("/drama/" + book.getString("bookId"));
                            vod.setVodName(book.optString("bookName", ""));
                            vod.setVodPic(book.optString("coverWap", ""));
                            vod.setVodRemarks((book.optString("statusDesc", "") + " " + book.optString("totalChapterNum", "") + "集").trim());
                            videos.add(vod);
                        }
                    }
                }
            }
        }
        
        // 去重处理
        Set<String> seen = new HashSet<>();
        List<Vod> uniqueVideos = new ArrayList<>();
        for (Vod video : videos) {
            String key = video.getVodId() + "_" + video.getVodName();
            if (!seen.contains(key)) {
                seen.add(key);
                uniqueVideos.add(video);
            }
        }
        
        videos = uniqueVideos;
        
        JSONObject result = new JSONObject();
        JSONArray listArray = new JSONArray();
        for (Vod vod : videos) {
            JSONObject vodObj = new JSONObject();
            vodObj.put("vod_id", vod.getVodId());
            vodObj.put("vod_name", vod.getVodName());
            vodObj.put("vod_pic", vod.getVodPic());
            vodObj.put("vod_remarks", vod.getVodRemarks());
            listArray.put(vodObj);
        }
        result.put("list", listArray);
        return result.toString();
    }
    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        JSONObject result = new JSONObject();
        result.put("page", Integer.parseInt(pg));
        result.put("pagecount", 1);
        result.put("limit", 20);
        result.put("total", 0);
        
        List<Vod> videos = new ArrayList<>();
        String url = siteUrl + "/browse/" + tid + "/" + pg;
        
        Map<String, String> response = fetch(url);
        if (response == null || !response.containsKey("body")) {
            result.put("list", new JSONArray());
            return result.toString();
        }
        
        String htmlContent = response.get("body");
        Pattern pattern = Pattern.compile("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(htmlContent);
        if (!matcher.find()) {
            result.put("list", new JSONArray());
            return result.toString();
        }
        
        try {
            JSONObject nextDataJson = new JSONObject(matcher.group(1));
            JSONObject pageProps = nextDataJson.optJSONObject("props").optJSONObject("pageProps");
            
            int currentPage = pageProps.optInt("page", 1);
            int totalPages = pageProps.optInt("pages", 1);
            JSONArray bookList = pageProps.optJSONArray("bookList");
            
            if (bookList != null) {
                for (int i = 0; i < bookList.length(); i++) {
                    JSONObject book = bookList.getJSONObject(i);
                    if (book.has("bookId")) {
                        Vod vod = new Vod();
                        vod.setVodId("/drama/" + book.getString("bookId"));
                        vod.setVodName(book.optString("bookName", ""));
                        vod.setVodPic(book.optString("coverWap", ""));
                        vod.setVodRemarks((book.optString("statusDesc", "") + " " + book.optString("totalChapterNum", "") + "集").trim());
                        videos.add(vod);
                    }
                }
            }
            
            result.put("page", currentPage);
            result.put("pagecount", totalPages);
            result.put("limit", videos.size());
            result.put("total", videos.size() * totalPages);
            
            JSONArray listArray = new JSONArray();
            for (Vod vod : videos) {
                JSONObject vodObj = new JSONObject();
                vodObj.put("vod_id", vod.getVodId());
                vodObj.put("vod_name", vod.getVodName());
                vodObj.put("vod_pic", vod.getVodPic());
                vodObj.put("vod_remarks", vod.getVodRemarks());
                listArray.put(vodObj);
            }
            result.put("list", listArray);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return result.toString();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContentPage(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        return searchContentPage(key, quick, pg);
    }

    private String searchContentPage(String key, boolean quick, String pg) throws Exception {
        JSONObject result = new JSONObject();
        result.put("page", Integer.parseInt(pg));
        result.put("pagecount", 1);
        result.put("limit", 20);
        result.put("total", 0);
        
        List<Vod> videos = new ArrayList<>();
        String searchUrl = siteUrl + "/search?searchValue=" + URLEncoder.encode(key, "UTF-8") + "&page=" + pg;
        
        Map<String, String> response = fetch(searchUrl);
        if (response == null || !response.containsKey("body")) {
            result.put("list", new JSONArray());
            return result.toString();
        }
        
        String htmlContent = response.get("body");
        Pattern pattern = Pattern.compile("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(htmlContent);
        if (!matcher.find()) {
            result.put("list", new JSONArray());
            return result.toString();
        }
        
        try {
            JSONObject nextDataJson = new JSONObject(matcher.group(1));
            JSONObject pageProps = nextDataJson.optJSONObject("props").optJSONObject("pageProps");
            
            int totalPages = pageProps.optInt("pages", 1);
            JSONArray bookList = pageProps.optJSONArray("bookList");
            
            if (bookList != null) {
                for (int i = 0; i < bookList.length(); i++) {
                    JSONObject book = bookList.getJSONObject(i);
                    if (book.has("bookId")) {
                        Vod vod = new Vod();
                        vod.setVodId("/drama/" + book.getString("bookId"));
                        vod.setVodName(book.optString("bookName", ""));
                        vod.setVodPic(book.optString("coverWap", ""));
                        vod.setVodRemarks((book.optString("statusDesc", "") + " " + book.optString("totalChapterNum", "") + "集").trim());
                        videos.add(vod);
                    }
                }
            }
            
            result.put("pagecount", totalPages);
            result.put("total", videos.size() * totalPages);
            
            JSONArray listArray = new JSONArray();
            for (Vod vod : videos) {
                JSONObject vodObj = new JSONObject();
                vodObj.put("vod_id", vod.getVodId());
                vodObj.put("vod_name", vod.getVodName());
                vodObj.put("vod_pic", vod.getVodPic());
                vodObj.put("vod_remarks", vod.getVodRemarks());
                listArray.put(vodObj);
            }
            result.put("list", listArray);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return result.toString();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        JSONObject result = new JSONObject();
        JSONArray listArray = new JSONArray();
        
        if (ids == null || ids.isEmpty()) {
            result.put("list", listArray);
            return result.toString();
        }
        
        String vodId = ids.get(0);
        if (!vodId.startsWith("/drama/")) {
            vodId = "/drama/" + vodId;
        }
        
        String dramaUrl = siteUrl + vodId;
        Map<String, String> response = fetch(dramaUrl);
        if (response == null || !response.containsKey("body")) {
            result.put("list", listArray);
            return result.toString();
        }
        
        String html = response.get("body");
        Pattern pattern = Pattern.compile("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(html);
        if (!matcher.find()) {
            result.put("list", listArray);
            return result.toString();
        }
        
        try {
            JSONObject nextData = new JSONObject(matcher.group(1));
            JSONObject pageProps = nextData.optJSONObject("props").optJSONObject("pageProps");
            JSONObject bookInfo = pageProps.optJSONObject("bookInfoVo");
            JSONArray chapterList = pageProps.optJSONArray("chapterList");
            
            if (bookInfo == null || !bookInfo.has("bookId")) {
                result.put("list", listArray);
                return result.toString();
            }
            
            // 基本信息
            JSONArray categoryList = bookInfo.optJSONArray("categoryList");
            List<String> categories = new ArrayList<>();
            if (categoryList != null) {
                for (int i = 0; i < categoryList.length(); i++) {
                    JSONObject category = categoryList.getJSONObject(i);
                    categories.add(category.optString("name", ""));
                }
            }
            
            JSONArray performerList = bookInfo.optJSONArray("performerList");
            List<String> performers = new ArrayList<>();
            if (performerList != null) {
                for (int i = 0; i < performerList.length(); i++) {
                    JSONObject performer = performerList.getJSONObject(i);
                    performers.add(performer.optString("name", ""));
                }
            }
            
            Vod vod = new Vod();
            vod.setVodId(vodId);
            vod.setVodName(bookInfo.optString("title", ""));
            vod.setVodPic(bookInfo.optString("coverWap", ""));
            vod.setTypeName(TextUtils.join(",", categories));
            vod.setVodArea(bookInfo.optString("countryName", ""));
            vod.setVodRemarks((bookInfo.optString("statusDesc", "") + " " + bookInfo.optString("totalChapterNum", "") + "集").trim());
            vod.setVodActor(TextUtils.join(", ", performers));
            vod.setVodContent(bookInfo.optString("introduction", ""));
            
            // 处理剧集
            List<String> playUrls = processEpisodes(vodId, chapterList);
            if (!playUrls.isEmpty()) {
                vod.setVodPlayFrom("河马剧场");
                vod.setVodPlayUrl(TextUtils.join("$$$", playUrls));
            }
            
            JSONObject vodObj = new JSONObject();
            vodObj.put("vod_id", vod.getVodId());
            vodObj.put("vod_name", vod.getVodName());
            vodObj.put("vod_pic", vod.getVodPic());
            vodObj.put("type_name", vod.getTypeName());
            vodObj.put("vod_area", vod.getVodArea());
            vodObj.put("vod_remarks", vod.getVodRemarks());
            vodObj.put("vod_actor", vod.getVodActor());
            vodObj.put("vod_content", vod.getVodContent());
            vodObj.put("vod_play_from", vod.getVodPlayFrom());
            vodObj.put("vod_play_url", vod.getVodPlayUrl());
            
            listArray.put(vodObj);
            result.put("list", listArray);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return result.toString();
    }

    private List<String> processEpisodes(String vodId, JSONArray chapterList) {
        List<String> playUrls = new ArrayList<>();
        List<String> episodes = new ArrayList<>();
        
        if (chapterList != null) {
            for (int i = 0; i < chapterList.length(); i++) {
                try {
                    JSONObject chapter = chapterList.getJSONObject(i);
                    String chapterId = chapter.optString("chapterId", "");
                    String chapterName = chapter.optString("chapterName", "");
                    
                    if (chapterId.isEmpty() || chapterName.isEmpty()) {
                        continue;
                    }
                    
                    // 尝试获取直接视频链接
                    String videoUrl = getDirectVideoUrl(chapter);
                    if (videoUrl != null && !videoUrl.isEmpty()) {
                        episodes.add(chapterName + "$" + videoUrl);
                        continue;
                    }
                    
                    // 回退方案
                    episodes.add(chapterName + "$" + vodId + "$" + chapterId + "$" + chapterName);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        
        if (!episodes.isEmpty()) {
            playUrls.add(TextUtils.join("#", episodes));
        }
        
        return playUrls;
    }

    private String getDirectVideoUrl(JSONObject chapter) {
        try {
            if (!chapter.has("chapterVideoVo") || chapter.isNull("chapterVideoVo")) {
                return null;
            }
            
            JSONObject videoInfo = chapter.getJSONObject("chapterVideoVo");
            String[] keys = {"mp4", "mp4720p", "vodMp4Url"};
            for (String key : keys) {
                if (videoInfo.has(key) && !videoInfo.isNull(key)) {
                    String url = videoInfo.getString(key);
                    if (url.toLowerCase().contains(".mp4")) {
                        return url;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        JSONObject result = new JSONObject();
        result.put("parse", 0);
        result.put("url", id);
        result.put("header", new JSONObject(headers).toString());
        
        // 如果已经是视频链接直接返回
        if (id.contains("http") && (id.contains(".mp4") || id.contains(".m3u8"))) {
            return result.toString();
        }
        
        // 解析参数
        String[] parts = id.split("\\$");
        if (parts.length < 2) {
            return result.toString();
        }
        
        String dramaId = parts[0].replace("/drama/", "");
        String chapterId = parts[1];
        
        // 尝试获取视频链接
        String videoUrl = getEpisodeVideoUrl(dramaId, chapterId);
        if (videoUrl != null && !videoUrl.isEmpty()) {
            result.put("url", videoUrl);
        }
        
        return result.toString();
    }

    private String getEpisodeVideoUrl(String dramaId, String chapterId) {
        try {
            String episodeUrl = siteUrl + "/episode/" + dramaId + "/" + chapterId;
            Map<String, String> response = fetch(episodeUrl);
            if (response == null || !response.containsKey("body")) {
                return null;
            }
            
            String html = response.get("body");
            
            // 方法1: 从NEXT_DATA提取
            Pattern pattern = Pattern.compile("<script id=\"__NEXT_DATA__\".*?>(.*?)</script>", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(html);
            if (matcher.find()) {
                try {
                    JSONObject nextData = new JSONObject(matcher.group(1));
                    JSONObject pageProps = nextData.optJSONObject("props").optJSONObject("pageProps");
                    JSONObject chapterInfo = pageProps.optJSONObject("chapterInfo");
                    
                    if (chapterInfo != null && chapterInfo.has("chapterVideoVo")) {
                        JSONObject videoInfo = chapterInfo.getJSONObject("chapterVideoVo");
                        String[] keys = {"mp4", "mp4720p", "vodMp4Url"};
                        for (String key : keys) {
                            if (videoInfo.has(key) && !videoInfo.isNull(key)) {
                                String url = videoInfo.getString(key);
                                if (url.toLowerCase().contains(".mp4")) {
                                    return url;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            
            // 方法2: 直接从HTML提取
            Pattern mp4Pattern = Pattern.compile("(https?://[^\"']+\\.mp4)");
            Matcher mp4Matcher = mp4Pattern.matcher(html);
            List<String> mp4Matches = new ArrayList<>();
            while (mp4Matcher.find()) {
                mp4Matches.add(mp4Matcher.group(1));
            }
            
            if (!mp4Matches.isEmpty()) {
                for (String url : mp4Matches) {
                    if (url.contains(chapterId) || url.contains(dramaId)) {
                        return url;
                    }
                }
                return mp4Matches.get(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return null;
    }

    private Map<String, String> fetch(String url) throws Exception {
        return fetch(url, null, 2);
    }
    private Map<String, String> fetch(String url, Map<String, String> customHeaders, int retry) throws Exception {
        if (customHeaders == null) {
            customHeaders = headers;
        }
        
        for (int i = 0; i <= retry; i++) {
            try {
                String response = OkHttp.string(url, customHeaders);
                Map<String, String> result = new HashMap<>();
                result.put("body", response);
                return result;
            } catch (Exception e) {
                if (i == retry) {
                    throw e;
                }
                Thread.sleep(1000);
            }
        }
        return null;
    }
    @Override
    public boolean manualVideoCheck() throws Exception {
        return false;
    }

    @Override
    public boolean isVideoFormat(String url) throws Exception {
        String[] videoFormats = {".mp4", ".mkv", ".avi", ".wmv", ".m3u8", ".flv", ".rmvb"};
        for (String format : videoFormats) {
            if (url.toLowerCase().contains(format)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void destroy() {
        super.destroy();
    }
}