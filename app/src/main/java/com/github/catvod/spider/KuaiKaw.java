package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
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

public class KuaiKaw extends Spider {

    private static final String SITE_URL = "https://www.kuaikaw.cn";
    private static final String NEXT_DATA_PATTERN = "<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>";
    private static final String[] VIDEO_KEYS = {"mp4", "mp4720p", "vodMp4Url"};
    
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
        put("User-Agent", Util.CHROME);
        put("Referer", SITE_URL);
        put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8");
        put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
    }};

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
    }

    private Map<String, String> getHeader() {
        return headers;
    }

    private JSONObject extractNextData(String html) {
        try {
            Pattern pattern = Pattern.compile(NEXT_DATA_PATTERN, Pattern.DOTALL);
            Matcher matcher = pattern.matcher(html);
            if (matcher.find()) {
                return new JSONObject(matcher.group(1));
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return null;
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
            SpiderDebug.log(e);
        }
        
        return Result.string(classes, list);
    }

    @Override
    public String homeVideoContent() throws Exception {
        List<Vod> videos = new ArrayList<>();
        String html = OkHttp.string(SITE_URL, getHeader());
        
        JSONObject nextData = extractNextData(html);
        if (nextData == null) {
            return Result.string(new ArrayList<Vod>());
        }
        
        JSONObject pageProps = nextData.optJSONObject("props").optJSONObject("pageProps");
        if (pageProps == null) {
            return Result.string(new ArrayList<Vod>());
        }
        
        Set<String> seen = new HashSet<>();
        List<Vod> uniqueVideos = new ArrayList<>();
        
        JSONArray bannerList = pageProps.optJSONArray("bannerList");
        if (bannerList != null) {
            for (int i = 0; i < bannerList.length(); i++) {
                JSONObject book = bannerList.getJSONObject(i);
                if (book.has("bookId")) {
                    String bookId = book.optString("bookId");
                    String bookName = book.optString("bookName");
                    String key = bookId + "_" + bookName;
                    
                    if (!seen.contains(key)) {
                        seen.add(key);
                        Vod vod = new Vod();
                        vod.setVodId("/drama/" + bookId);
                        vod.setVodName(bookName);
                        vod.setVodPic(book.optString("coverWap", ""));
                        vod.setVodRemarks((book.optString("statusDesc", "") + " " + book.optString("totalChapterNum", "") + "集").trim());
                        uniqueVideos.add(vod);
                    }
                }
            }
        }
        
        JSONArray seoColumnVos = pageProps.optJSONArray("seoColumnVos");
        if (seoColumnVos != null) {
            for (int i = 0; i < seoColumnVos.length(); i++) {
                JSONArray bookInfos = seoColumnVos.getJSONObject(i).optJSONArray("bookInfos");
                if (bookInfos != null) {
                    for (int j = 0; j < bookInfos.length(); j++) {
                        JSONObject book = bookInfos.getJSONObject(j);
                        if (book.has("bookId")) {
                            String bookId = book.optString("bookId");
                            String bookName = book.optString("bookName");
                            String key = bookId + "_" + bookName;
                            
                            if (!seen.contains(key)) {
                                seen.add(key);
                                Vod vod = new Vod();
                                vod.setVodId("/drama/" + bookId);
                                vod.setVodName(bookName);
                                vod.setVodPic(book.optString("coverWap", ""));
                                vod.setVodRemarks((book.optString("statusDesc", "") + " " + book.optString("totalChapterNum", "") + "集").trim());
                                uniqueVideos.add(vod);
                            }
                        }
                    }
                }
            }
        }
        
        return Result.string(uniqueVideos);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        List<Vod> videos = new ArrayList<>();
        String url = SITE_URL + "/browse/" + tid + "/" + pg;
        
        String html = OkHttp.string(url, getHeader());
        JSONObject nextData = extractNextData(html);
        if (nextData == null) {
            return Result.string(videos);
        }
        
        JSONObject pageProps = nextData.optJSONObject("props").optJSONObject("pageProps");
        if (pageProps == null) {
            return Result.string(videos);
        }
        
        JSONArray bookList = pageProps.optJSONArray("bookList");
        if (bookList != null) {
            for (int i = 0; i < bookList.length(); i++) {
                JSONObject book = bookList.getJSONObject(i);
                if (book.has("bookId")) {
                    Vod vod = new Vod();
                    vod.setVodId("/drama/" + book.optString("bookId"));
                    vod.setVodName(book.optString("bookName", ""));
                    vod.setVodPic(book.optString("coverWap", ""));
                    vod.setVodRemarks((book.optString("statusDesc", "") + " " + book.optString("totalChapterNum", "") + "集").trim());
                    videos.add(vod);
                }
            }
        }
        
        return Result.string(videos);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        List<Vod> videos = new ArrayList<>();
        String searchUrl = SITE_URL + "/search?searchValue=" + URLEncoder.encode(key, "UTF-8") + "&page=" + pg;
        
        String html = OkHttp.string(searchUrl, getHeader());
        JSONObject nextData = extractNextData(html);
        if (nextData == null) {
            return Result.string(videos);
        }
        
        JSONObject pageProps = nextData.optJSONObject("props").optJSONObject("pageProps");
        if (pageProps == null) {
            return Result.string(videos);
        }
        
        JSONArray bookList = pageProps.optJSONArray("bookList");
        if (bookList != null) {
            for (int i = 0; i < bookList.length(); i++) {
                JSONObject book = bookList.getJSONObject(i);
                if (book.has("bookId")) {
                    Vod vod = new Vod();
                    vod.setVodId("/drama/" + book.optString("bookId"));
                    vod.setVodName(book.optString("bookName", ""));
                    vod.setVodPic(book.optString("coverWap", ""));
                    vod.setVodRemarks((book.optString("statusDesc", "") + " " + book.optString("totalChapterNum", "") + "集").trim());
                    videos.add(vod);
                }
            }
        }
        
        return Result.string(videos);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) {
            return Result.string(new Vod());
        }
        
        String vodId = ids.get(0);
        if (!vodId.startsWith("/drama/")) {
            vodId = "/drama/" + vodId;
        }
        
        String dramaUrl = SITE_URL + vodId;
        String html = OkHttp.string(dramaUrl, getHeader());
        
        JSONObject nextData = extractNextData(html);
        if (nextData == null) {
            return Result.string(new Vod());
        }
        
        JSONObject pageProps = nextData.optJSONObject("props").optJSONObject("pageProps");
        if (pageProps == null) {
            return Result.string(new Vod());
        }
        
        JSONObject bookInfo = pageProps.optJSONObject("bookInfoVo");
        JSONArray chapterList = pageProps.optJSONArray("chapterList");
        
        if (bookInfo == null || !bookInfo.has("bookId")) {
            return Result.string(new Vod());
        }
        
        Vod vod = new Vod();
        vod.setVodId(vodId);
        vod.setVodName(bookInfo.optString("title", ""));
        vod.setVodPic(bookInfo.optString("coverWap", ""));
        vod.setVodArea(bookInfo.optString("countryName", ""));
        vod.setVodRemarks((bookInfo.optString("statusDesc", "") + " " + bookInfo.optString("totalChapterNum", "") + "集").trim());
        vod.setVodContent(bookInfo.optString("introduction", ""));
        
        JSONArray categoryList = bookInfo.optJSONArray("categoryList");
        List<String> categories = new ArrayList<>();
        if (categoryList != null) {
            for (int i = 0; i < categoryList.length(); i++) {
                categories.add(categoryList.getJSONObject(i).optString("name", ""));
            }
        }
        vod.setTypeName(TextUtils.join(",", categories));
        
        JSONArray performerList = bookInfo.optJSONArray("performerList");
        List<String> performers = new ArrayList<>();
        if (performerList != null) {
            for (int i = 0; i < performerList.length(); i++) {
                performers.add(performerList.getJSONObject(i).optString("name", ""));
            }
        }
        vod.setVodActor(TextUtils.join(", ", performers));
        
        List<String> playUrls = processEpisodes(vodId, chapterList);
        if (!playUrls.isEmpty()) {
            vod.setVodPlayFrom("河马剧场");
            vod.setVodPlayUrl(TextUtils.join("$$$", playUrls));
        }
        
        return Result.string(vod);
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
                    
                    String videoUrl = extractVideoUrl(chapter);
                    if (videoUrl != null && !videoUrl.isEmpty()) {
                        episodes.add(chapterName + "$" + videoUrl);
                    } else {
                        episodes.add(chapterName + "$" + vodId + "$" + chapterId + "$" + chapterName);
                    }
                } catch (Exception e) {
                    SpiderDebug.log(e);
                }
            }
        }
        
        if (!episodes.isEmpty()) {
            playUrls.add(TextUtils.join("#", episodes));
        }
        
        return playUrls;
    }

    private String extractVideoUrl(JSONObject chapter) {
        try {
            if (!chapter.has("chapterVideoVo") || chapter.isNull("chapterVideoVo")) {
                return null;
            }
            
            JSONObject videoInfo = chapter.getJSONObject("chapterVideoVo");
            for (String key : VIDEO_KEYS) {
                if (videoInfo.has(key) && !videoInfo.isNull(key)) {
                    String url = videoInfo.getString(key);
                    if (url.toLowerCase().contains(".mp4")) {
                        return url;
                    }
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return null;
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if (id.contains("http") && (id.contains(".mp4") || id.contains(".m3u8"))) {
            return Result.get().url(id).header(getHeader()).string();
        }
        
        String[] parts = id.split("\\$");
        if (parts.length < 2) {
            return Result.get().url(id).header(getHeader()).string();
        }
        
        String dramaId = parts[0].replace("/drama/", "");
        String chapterId = parts[1];
        
        String videoUrl = getEpisodeVideoUrl(dramaId, chapterId);
        if (videoUrl != null && !videoUrl.isEmpty()) {
            return Result.get().url(videoUrl).header(getHeader()).string();
        }
        
        return Result.get().url(id).header(getHeader()).string();
    }

    private String getEpisodeVideoUrl(String dramaId, String chapterId) {
        try {
            String episodeUrl = SITE_URL + "/episode/" + dramaId + "/" + chapterId;
            String html = OkHttp.string(episodeUrl, getHeader());
            
            JSONObject nextData = extractNextData(html);
            if (nextData != null) {
                JSONObject pageProps = nextData.optJSONObject("props").optJSONObject("pageProps");
                JSONObject chapterInfo = pageProps != null ? pageProps.optJSONObject("chapterInfo") : null;
                
                if (chapterInfo != null && chapterInfo.has("chapterVideoVo")) {
                    JSONObject videoInfo = chapterInfo.getJSONObject("chapterVideoVo");
                    for (String key : VIDEO_KEYS) {
                        if (videoInfo.has(key) && !videoInfo.isNull(key)) {
                            String url = videoInfo.getString(key);
                            if (url.toLowerCase().contains(".mp4")) {
                                return url;
                            }
                        }
                    }
                }
            }
            
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
            SpiderDebug.log(e);
        }
        
        return null;
    }

    @Override
    public boolean manualVideoCheck() throws Exception {
        return false;
    }

    @Override
    public boolean isVideoFormat(String url) throws Exception {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        String lowerUrl = url.toLowerCase();
        return lowerUrl.contains(".mp4") || lowerUrl.contains(".m3u8") || 
               lowerUrl.contains(".mkv") || lowerUrl.contains(".avi") || 
               lowerUrl.contains(".flv") || lowerUrl.contains(".wmv");
    }

    @Override
    public void destroy() {
        super.destroy();
    }
}
