package com.github.catvod.spider;

import android.content.Context;
import android.net.Uri;

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
import java.util.List;
import java.util.Map;

public class KuaiKaw extends Spider {

    private static final String siteUrl = "https://www.kuaikaw.cn";
    private static final String apiUrl = "https://api.hmjc.top/api.php";
    private String apiKey = "";

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        if (extend != null && !extend.isEmpty()) {
            try {
                JSONObject config = new JSONObject(extend);
                if (config.has("api_key")) {
                    apiKey = config.getString("api_key");
                }
            } catch (Exception e) {
                apiKey = extend;
            }
        }
    }

    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", Util.CHROME);
        header.put("Referer", siteUrl);
        header.put("Accept", "application/json");
        return header;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("1", "热门推荐"));
        classes.add(new Class("2", "最新上线"));
        classes.add(new Class("3", "古装仙侠"));
        classes.add(new Class("4", "都市情感"));
        classes.add(new Class("5", "豪门恩怨"));
        classes.add(new Class("6", "青春甜宠"));
        classes.add(new Class("7", "悬疑推理"));
        classes.add(new Class("8", "其他"));

        List<Vod> list = new ArrayList<>();
        try {
            String url = apiUrl + "?apikey=" + apiKey + "&type=hot&page=1";
            String json = OkHttp.string(url, getHeader());
            JSONObject resp = new JSONObject(json);
            if (resp.has("list")) {
                JSONArray array = resp.getJSONArray("list");
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.getJSONObject(i);
                    String vodId = item.optString("id", "");
                    String vodName = item.optString("name", item.optString("title", ""));
                    String vodPic = item.optString("pic", item.optString("cover", ""));
                    String vodRemarks = item.optString("remarks", item.optString("note", ""));
                    list.add(new Vod(vodId, vodName, vodPic, vodRemarks));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return Result.string(classes, list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter,
                                   HashMap<String, String> extend) throws Exception {
        List<Vod> list = new ArrayList<>();
        try {
            String url = apiUrl + "?apikey=" + apiKey + "&type=category&cid=" + tid + "&page=" + pg;
            String json = OkHttp.string(url, getHeader());
            JSONObject resp = new JSONObject(json);
            if (resp.has("list")) {
                JSONArray array = resp.getJSONArray("list");
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.getJSONObject(i);
                    String vodId = item.optString("id", "");
                    String vodName = item.optString("name", item.optString("title", ""));
                    String vodPic = item.optString("pic", item.optString("cover", ""));
                    String vodRemarks = item.optString("remarks", item.optString("note", ""));
                    list.add(new Vod(vodId, vodName, vodPic, vodRemarks));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.string(list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        Vod vod = new Vod();
        try {
            String url = apiUrl + "?apikey=" + apiKey + "&type=detail&id=" + ids.get(0);
            String json = OkHttp.string(url, getHeader());
            JSONObject resp = new JSONObject(json);
            
            vod.setVodId(ids.get(0));
            vod.setVodName(resp.optString("name", resp.optString("title", "")));
            vod.setVodPic(resp.optString("pic", resp.optString("cover", "")));
            vod.setVodContent(resp.optString("content", resp.optString("desc", "")));
            vod.setVodDirector(resp.optString("director", ""));
            vod.setVodActor(resp.optString("actor", ""));
            vod.setVodYear(resp.optString("year", ""));
            vod.setVodArea(resp.optString("area", ""));

            StringBuilder playFrom = new StringBuilder();
            StringBuilder playUrl = new StringBuilder();
            
            if (resp.has("episodes")) {
                JSONArray episodes = resp.getJSONArray("episodes");
                playFrom.append("默认");
                for (int i = 0; i < episodes.length(); i++) {
                    JSONObject ep = episodes.getJSONObject(i);
                    String epName = ep.optString("name", "第" + (i + 1) + "集");
                    String epUrl = ep.optString("url", ep.optString("video_id", ""));
                    playUrl.append(epName).append("$").append(epUrl);
                    if (i < episodes.length() - 1) {
                        playUrl.append("#");
                    }
                }
            } else if (resp.has("play_list")) {
                JSONArray playList = resp.getJSONArray("play_list");
                for (int i = 0; i < playList.length(); i++) {
                    JSONObject source = playList.getJSONObject(i);
                    playFrom.append(source.optString("from", "线路" + (i + 1)));
                    if (i < playList.length() - 1) {
                        playFrom.append("$$$");
                    }
                    
                    JSONArray eps = source.optJSONArray("episodes");
                    if (eps != null) {
                        for (int j = 0; j < eps.length(); j++) {
                            JSONObject ep = eps.getJSONObject(j);
                            String epName = ep.optString("name", "第" + (j + 1) + "集");
                            String epUrl = ep.optString("url", ep.optString("video_id", ""));
                            playUrl.append(epName).append("$").append(epUrl);
                            if (j < eps.length() - 1) {
                                playUrl.append("#");
                            }
                        }
                    }
                    if (i < playList.length() - 1) {
                        playUrl.append("$$$");
                    }
                }
            }

            vod.setVodPlayFrom(playFrom.toString());
            vod.setVodPlayUrl(playUrl.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        List<Vod> list = new ArrayList<>();
        try {
            String encodedKey = URLEncoder.encode(key, "UTF-8");
            String url = apiUrl + "?apikey=" + apiKey + "&type=search&keyword=" + encodedKey + "&page=1";
            String json = OkHttp.string(url, getHeader());
            JSONObject resp = new JSONObject(json);
            if (resp.has("list")) {
                JSONArray array = resp.getJSONArray("list");
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.getJSONObject(i);
                    String vodId = item.optString("id", "");
                    String vodName = item.optString("name", item.optString("title", ""));
                    String vodPic = item.optString("pic", item.optString("cover", ""));
                    String vodRemarks = item.optString("remarks", item.optString("note", ""));
                    list.add(new Vod(vodId, vodName, vodPic, vodRemarks));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.string(list);
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        List<Vod> list = new ArrayList<>();
        try {
            String encodedKey = URLEncoder.encode(key, "UTF-8");
            String url = apiUrl + "?apikey=" + apiKey + "&type=search&keyword=" + encodedKey + "&page=" + pg;
            String json = OkHttp.string(url, getHeader());
            JSONObject resp = new JSONObject(json);
            if (resp.has("list")) {
                JSONArray array = resp.getJSONArray("list");
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.getJSONObject(i);
                    String vodId = item.optString("id", "");
                    String vodName = item.optString("name", item.optString("title", ""));
                    String vodPic = item.optString("pic", item.optString("cover", ""));
                    String vodRemarks = item.optString("remarks", item.optString("note", ""));
                    list.add(new Vod(vodId, vodName, vodPic, vodRemarks));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        try {
            String url = apiUrl + "?apikey=" + apiKey + "&type=video&video_id=" + id;
            String json = OkHttp.string(url, getHeader());
            JSONObject resp = new JSONObject(json);
            String playUrl = resp.optString("url", resp.optString("play_url", ""));
            if (!playUrl.isEmpty()) {
                return Result.get().url(playUrl).header(getHeader()).string();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.get().url("").string();
    }
}
