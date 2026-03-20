package com.github.catvod.spider;

import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Ting39 extends Spider {

    private static final String SITE_URL = "https://www.ting39.com";
    private static final Map<String, String> HEADERS = new HashMap<>();

    static {
        HEADERS.put("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36");
    }

    private Document fetchDocument(String url) throws Exception {
        String content = OkHttp.string(url, HEADERS);
        return Jsoup.parse(content);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        Document doc = fetchDocument(SITE_URL + "/book/all/lastupdate.html");

        JSONArray classes = new JSONArray();
        JSONObject filters = new JSONObject();

        Elements categoryLinks = doc.select("div.r-zz a");
        for (Element a : categoryLinks) {
            String name = a.text().trim();
            if (name.equals("全部有声") || name.equals("明星电台") || name.equals("乡村生活") || name.equals("幻想言情")) {
                continue;
            }

            String tid = a.attr("href");

            JSONObject cls = new JSONObject();
            cls.put("type_id", tid);
            cls.put("type_name", name);

            filters.put(tid, new JSONArray()); // 预留过滤器

            classes.put(cls);
        }

        JSONObject result = new JSONObject();
        result.put("class", classes);
        if (filter) {
            result.put("filters", filters);
        }
        return result.toString();
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page = (pg == null || pg.isEmpty()) ? 1 : Integer.parseInt(pg);
        String[] parts = tid.split("\\.html");
        String url = SITE_URL + parts[0] + "/" + page + ".html";

        Document doc = fetchDocument(url);

        JSONArray list = new JSONArray();
        Elements items = doc.select("ul.list-works li");
        for (Element item : items) {
            Element imgBox = item.selectFirst("div.list-imgbox");
            if (imgBox == null) continue;

            Element a = imgBox.selectFirst("a");
            if (a == null) continue;

            String name = a.attr("title").trim();
            String vid = a.attr("href");
            Element img = item.selectFirst("img.lazy");
            String pic = (img != null ? img.attr("data-original") : "").trim();
            if (!pic.startsWith("http")) pic = SITE_URL + pic;

            String remark = "";
            Element playCount = item.selectFirst("div.playCountText");
            if (playCount != null) {
                remark = playCount.text().trim() + " 播放量";
            }

            JSONObject vod = new JSONObject();
            vod.put("vod_id", vid);
            vod.put("vod_name", name);
            vod.put("vod_pic", pic);
            vod.put("vod_remarks", remark);
            list.put(vod);
        }

        JSONObject result = new JSONObject();
        result.put("page", page);
        result.put("pagecount", 9999);
        result.put("limit", 90);
        result.put("total", 999999);
        result.put("list", list);

        return result.toString();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String did = ids.get(0);
        if (!did.startsWith("http")) did = SITE_URL + did;

        Document doc = fetchDocument(did);

        Element fr = doc.selectFirst("div.fr");
        if (fr == null) {
            return "{\"list\":[]}";
        }
        String playPage = SITE_URL + fr.selectFirst("a").attr("href");

        String content = OkHttp.string(playPage, HEADERS);
        Matcher matcher = Pattern.compile("location\\.href=\"(.*?)\"").matcher(content);
        String location = matcher.find() ? matcher.group(1) : "";

        if (location.isEmpty()) {
            return "{\"list\":[]}";
        }

        String chapterBaseUrl = SITE_URL + location;
        doc = fetchDocument(chapterBaseUrl);

        List<String> allUrls = new ArrayList<>();
        allUrls.add(location); // 当前location即第一页

        Elements chapterWraps = doc.select("div.chapter-wrap.js_chapter_wrap");
        for (Element wrap : chapterWraps) {
            for (Element a : wrap.select("a")) {
                String href = a.attr("href");
                if (!href.isEmpty() && !"javascript:;".equals(href)) {
                    allUrls.add(href);
                }
            }
        }

        // 去重，保持顺序
        allUrls = new ArrayList<>(new LinkedHashSet<>(allUrls));

        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Future<String>> futures = new ArrayList<>();

        for (String pageUrl : allUrls) {
            futures.add(executor.submit(() -> {
                String fullUrl = SITE_URL + pageUrl;
                Document pageDoc = fetchDocument(fullUrl);

                StringBuilder sb = new StringBuilder();
                for (Element li : pageDoc.select("div.playlist li")) {
                    Element a = li.selectFirst("a");
                    if (a == null) continue;
                    String title = a.attr("title").trim();
                    String href = a.attr("href").trim();
                    if (!href.isEmpty()) {
                        sb.append(title).append("$").append(href).append("#");
                    }
                }
                if (sb.length() > 0) sb.deleteCharAt(sb.length() - 1);
                return sb.toString();
            }));
        }

        StringBuilder allPlay = new StringBuilder();
        for (Future<String> future : futures) {
            String part = future.get();
            if (!part.isEmpty()) {
                allPlay.append(part).append("#");
            }
        }
        if (allPlay.length() > 0) allPlay.deleteCharAt(allPlay.length() - 1);

        executor.shutdown(); // 所有任务已完成，只需关闭接受新任务

        JSONArray vodList = new JSONArray();
        JSONObject vod = new JSONObject();
        vod.put("vod_id", ids.get(0));
        vod.put("vod_play_from", "听书专线");
        vod.put("vod_play_url", allPlay.toString());
        vodList.put(vod);

        JSONObject result = new JSONObject();
        result.put("list", vodList);
        return result.toString();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        JSONObject result = new JSONObject();
        result.put("parse", 1);
        result.put("playUrl", "");
        result.put("url", SITE_URL + id);
        result.put("header", new JSONObject(HEADERS).toString());
        return result.toString();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContentPage(key, "1");
    }

    private String searchContentPage(String key, String pg) throws Exception {
        int page = (pg == null || pg.isEmpty()) ? 1 : Integer.parseInt(pg);
        String url = SITE_URL + "/search.html?searchtype=name&searchword=" + key + "&page=" + page;

        Document doc = fetchDocument(url);

        JSONArray list = new JSONArray();
        Elements items = doc.select("ul.list-works li");
        for (Element item : items) {
            Element imgBox = item.selectFirst("div.list-imgbox");
            if (imgBox == null) continue;

            Element a = imgBox.selectFirst("a");
            if (a == null) continue;

            String name = a.attr("title").trim();
            String vid = a.attr("href");
            Element img = item.selectFirst("img.lazy");
            String pic = (img != null ? img.attr("data-original") : "").trim();
            if (!pic.startsWith("http")) pic = SITE_URL + pic;

            String remark = "";
            Element status = item.selectFirst("span.book-zt");
            if (status != null) {
                remark = "更新日期" + status.text().trim();
            }

            JSONObject vod = new JSONObject();
            vod.put("vod_id", vid);
            vod.put("vod_name", name);
            vod.put("vod_pic", pic);
            vod.put("vod_remarks", remark);
            list.put(vod);
        }

        JSONObject result = new JSONObject();
        result.put("page", page);
        result.put("pagecount", 9999);
        result.put("limit", 90);
        result.put("total", 999999);
        result.put("list", list);

        return result.toString();
    }
}
