package com.visitor.system.visitor.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DingTalkOaMessage {

    private String title;

    private String headText;

    private String content;

    private String author;

    private String actionUrl;

    private List<FormItem> forms;

    @Getter
    @Builder
    public static class FormItem {
        private String key;
        private String value;
    }
}
