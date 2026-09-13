package com.visitor.system.visitor.service.impl;

import com.visitor.system.config.VisitorProperties;
import com.visitor.system.visitor.dto.DingTalkOaMessage;
import com.visitor.system.visitor.dto.DingTalkUserDto;
import com.visitor.system.visitor.service.DingTalkClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Primary
public class DelegatingDingTalkClient implements DingTalkClient {

    private final VisitorProperties properties;
    private final DingTalkClient mockClient;
    private final DingTalkClient httpClient;

    public DelegatingDingTalkClient(VisitorProperties properties,
                                   @Qualifier("mockDingTalkClient") DingTalkClient mockClient,
                                   @Qualifier("httpDingTalkClient") DingTalkClient httpClient) {
        this.properties = properties;
        this.mockClient = mockClient;
        this.httpClient = httpClient;
    }

    @Override
    public List<DingTalkUserDto> searchUsers(String keyword) {
        return delegate().searchUsers(keyword);
    }

    @Override
    public DingTalkUserDto getUserByAuthCode(String authCode) {
        return delegate().getUserByAuthCode(authCode);
    }

    @Override
    public void sendOaMessage(String userId, DingTalkOaMessage message) {
        delegate().sendOaMessage(userId, message);
    }

    private DingTalkClient delegate() {
        return properties.getDingtalk().isMockMode() ? mockClient : httpClient;
    }
}
