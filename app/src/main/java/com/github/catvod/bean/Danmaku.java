package com.github.catvod.bean;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;

public class Danmaku {

    @SerializedName("name")
    private String name;
    @SerializedName("url")
    private String url;
    @SerializedName("type")
    private String type;
    @SerializedName("color")
    private String color;
    @SerializedName("size")
    private Integer size;
    @SerializedName("from")
    private String from;
    @SerializedName("data")
    private String data;

    public static List<Danmaku> arrayFrom(String str) {
        Type listType = new TypeToken<List<Danmaku>>() {}.getType();
        return new Gson().fromJson(str, listType);
    }

    public static Danmaku objectFrom(String str) {
        return new Gson().fromJson(str, Danmaku.class);
    }

    public static Danmaku create() {
        return new Danmaku();
    }

    public Danmaku() {
    }

    public Danmaku name(String name) {
        this.name = name;
        return this;
    }

    public Danmaku url(String url) {
        this.url = url;
        return this;
    }

    public Danmaku type(String type) {
        this.type = type;
        return this;
    }

    public Danmaku color(String color) {
        this.color = color;
        return this;
    }

    public Danmaku size(Integer size) {
        this.size = size;
        return this;
    }

    public Danmaku from(String from) {
        this.from = from;
        return this;
    }

    public Danmaku data(String data) {
        this.data = data;
        return this;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public boolean hasName() {
        return name != null && !name.isEmpty();
    }

    public boolean hasUrl() {
        return url != null && !url.isEmpty();
    }

    public boolean hasType() {
        return type != null && !type.isEmpty();
    }

    public boolean hasColor() {
        return color != null && !color.isEmpty();
    }

    public boolean hasSize() {
        return size != null && size > 0;
    }

    public boolean hasFrom() {
        return from != null && !from.isEmpty();
    }

    public boolean hasData() {
        return data != null && !data.isEmpty();
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}