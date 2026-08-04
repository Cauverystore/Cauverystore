package com.cauverystore.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ChatResponse {

    private String reply;
    private String intent;
    private Map<String, Object> data;
    private List<Map<String, Object>> actions = new ArrayList<>();
    private List<String> quickReplies = new ArrayList<>();

    public static ChatResponse create(String intent, String reply) {
        ChatResponse r = new ChatResponse();
        r.setIntent(intent);
        r.setReply(reply);
        return r;
    }

    public ChatResponse data(Map<String, Object> data) {
        this.data = data;
        return this;
    }

    public ChatResponse actions(List<Map<String, Object>> actions) {
        this.actions = actions != null ? actions : new ArrayList<>();
        return this;
    }

    public ChatResponse quickReplies(List<String> quickReplies) {
        this.quickReplies = quickReplies != null ? quickReplies : new ArrayList<>();
        return this;
    }

    @java.lang.SuppressWarnings("all")
    public String getReply() {
        return this.reply;
    }

    @java.lang.SuppressWarnings("all")
    public String getIntent() {
        return this.intent;
    }

    @java.lang.SuppressWarnings("all")
    public Map<String, Object> getData() {
        return this.data;
    }

    @java.lang.SuppressWarnings("all")
    public List<Map<String, Object>> getActions() {
        return this.actions;
    }

    @java.lang.SuppressWarnings("all")
    public List<String> getQuickReplies() {
        return this.quickReplies;
    }

    @java.lang.SuppressWarnings("all")
    public void setReply(final String reply) {
        this.reply = reply;
    }

    @java.lang.SuppressWarnings("all")
    public void setIntent(final String intent) {
        this.intent = intent;
    }

    @java.lang.SuppressWarnings("all")
    public void setData(final Map<String, Object> data) {
        this.data = data;
    }

    @java.lang.SuppressWarnings("all")
    public void setActions(final List<Map<String, Object>> actions) {
        this.actions = actions;
    }

    @java.lang.SuppressWarnings("all")
    public void setQuickReplies(final List<String> quickReplies) {
        this.quickReplies = quickReplies;
    }
}
