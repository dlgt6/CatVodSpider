package com.github.catvod.spider;

import android.content.Context;
import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 锦鲤短剧 Java 版本
 * 已根据 Init.java 和 OkHttp.java 源码完成最终适配
 */
public class Jinli extends Spider {

    private String apiHost = "https://api.jinlidj.com";
    private Map<String, String> headerx;

    @Override
    public void init(Context context, String ext) throws Exception {
        // 直接向上抛出异常，解决 "unreported exception Exception" 编译错误
        super.init(context, ext);
        headerx = new HashMap<>();
        headerx.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36");
        headerx.put("Referer", "https://www.jinlidj.com/");
    }

    @Override
    public String homeContent(boolean filter) {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("1", "🌠情感关系"));
        classes.add(new Class("2", "🌠成长逆袭"));
        classes.add(new Class("3", "🌠奇幻异能"));
        classes.add(new Class("4", "🌠战斗热血"));
        classes.add(new Class("5", "🌠伦理现实"));
        classes.add(new Class("6", "🌠时空穿越"));
        classes.add(new Class("7", "🌠权谋身份"));
        // 显式转型 JSONObject 消除 Result.string(..., null) 的歧义
        return Result.string(classes, new ArrayList<Vod>(), (JSONObject) null);
    }

    @Override
    public String homeVideoContent() {
        return categoryContent("", "1", false, null);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("page", Integer.parseInt(pg));
            payload.put("limit", 24);
            payload.put("type_id", tid);
            payload.put("year", "");
            payload.put("keyword", "");

            // 适配 OkHttp 返回 OkResult 的逻辑
            String res = OkHttp.post(apiHost + "/api/search", payload.toString(), headerx).getBody();
            return parseList(res);
        } catch (Exception e) {
            return Result.get().string();
        }
    }

    @Override
    public String searchContent(String key, boolean quick) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("page", 1);
            payload.put("limit", 24);
            payload.put("type_id", "");
            payload.put("keyword", key);

            String res = OkHttp.post(apiHost + "/api/search", payload.toString(), headerx).getBody();
            return parseList(res);
        } catch (Exception e) {
            return Result.get().string();
        }
    }

    private String parseList(String jsonStr) throws Exception {
        JSONObject dataObj = new JSONObject(jsonStr);
        JSONArray list = dataObj.getJSONObject("data").getJSONArray("list");
        List<Vod> videos = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject v = list.getJSONObject(i);
            Vod vod = new Vod();
            vod.setVodId(v.optString("vod_id"));
            vod.setVodName(v.optString("vod_name"));
            vod.setVodPic(v.optString("vod_pic"));
            vod.setVodRemarks("▶️" + v.optString("vod_total", v.optString("vod_remarks")) + "集");
            videos.add(vod);
        }
        return Result.string(videos);
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String did = ids.get(0);
            String res = OkHttp.post(apiHost + "/api/detail/" + did, "{}", headerx).getBody();
            JSONObject data = new JSONObject(res).getJSONObject("data");

            Vod vod = new Vod();
            vod.setVodId(did);
            vod.setVodName(data.optString("vod_name"));
            vod.setVodPic(data.optString("vod_pic"));
            vod.setVodYear(data.optString("vod_year"));
            vod.setVodArea(data.optString("vod_area"));
            vod.setVodActor(data.optString("vod_actor"));
            vod.setVodDirector(data.optString("vod_director"));
            vod.setVodRemarks(data.optString("vod_tag"));
            vod.setVodContent("🎉剧情简介📢" + data.optString("vod_blurb"));

            JSONObject player = data.getJSONObject("player");
            List<String> playUrls = new ArrayList<>();
            Iterator<String> keys = player.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String val = player.getString(key);
                playUrls.add(key + "$" + val);
            }

            vod.setVodPlayFrom("锦鲤短剧");
            vod.setVodPlayUrl(android.text.TextUtils.join("#", playUrls));

            return Result.string(vod);
        } catch (Exception e) {
            return Result.get().string();
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            String playUrl = id + "&auto=1";
            // OkHttp.string 直接返回字符串，适配 playerContent 逻辑
            String html = OkHttp.string(playUrl, headerx);
            
            Pattern pattern = Pattern.compile("\"url\":\"(.*?)\"");
            Matcher matcher = pattern.matcher(html);
            if (matcher.find()) {
                String realUrl = matcher.group(1).replace("\\/", "/");
                return Result.get().url(realUrl).header(headerx).parse(0).string();
            }
            
            return Result.get().url(id).header(headerx).parse(0).string();
        } catch (Exception e) {
            return Result.get().string();
        }
    }
}
