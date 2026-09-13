package com.visitor.system.visitor.service;

import com.visitor.system.visitor.dto.DingTalkOaMessage;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class DingTalkNotifyService {

    private final DingTalkClient dingTalkClient;

    public DingTalkNotifyService(DingTalkClient dingTalkClient) {
        this.dingTalkClient = dingTalkClient;
    }

    @Async
    public void sendApproveNotification(String userId, DingTalkOaMessage message) {
        dingTalkClient.sendOaMessage(userId, message);
    }
}
