package com.visitor.system.visitor.service.impl;

import com.visitor.system.common.BusinessException;
import com.visitor.system.config.VisitorProperties;
import com.visitor.system.visitor.dto.DingTalkOaMessage;
import com.visitor.system.visitor.dto.DingTalkUserDto;
import com.visitor.system.visitor.service.DingTalkClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component("mockDingTalkClient")
public class MockDingTalkClient implements DingTalkClient {

    private static final List<DingTalkUserDto> USERS = List.of(
        new DingTalkUserDto("u1001", "张三", "行政部"),
        new DingTalkUserDto("u1002", "李四", "技术部"),
        new DingTalkUserDto("u1003", "王五", "销售部"),
        new DingTalkUserDto("u1004", "赵六", "安保部")
    );

    private final VisitorProperties properties;

    public MockDingTalkClient(VisitorProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<DingTalkUserDto> searchUsers(String keyword) {
        String normalized = keyword.toLowerCase(Locale.ROOT);
        List<DingTalkUserDto> results = USERS.stream()
            .filter(user -> user.getName().contains(keyword)
                || user.getDeptName().contains(keyword)
                || user.getUserId().toLowerCase(Locale.ROOT).contains(normalized))
            .toList();
        if (!results.isEmpty()) {
            return results;
        }
        // Mock 模式下允许用输入关键字即时生成一个候选人，避免联调被固定样例数据卡住。
        return List.of(new DingTalkUserDto("mock-" + Math.abs(keyword.hashCode()), keyword, "待确认部门"));
    }

    @Override
    public DingTalkUserDto getUserByAuthCode(String authCode) {
        String authCodePrefix = properties.getDingtalk().getMockAuthCodePrefix();
        if (authCode == null || !authCode.startsWith(authCodePrefix)) {
            throw new BusinessException("模拟 authCode 非法，请使用受信任的 mock 授权码");
        }
        String userId = authCode.substring(authCodePrefix.length());
        return USERS.stream()
            .filter(user -> user.getUserId().equals(userId))
            .findFirst()
            .orElseThrow(() -> new BusinessException("未找到模拟钉钉用户"));
    }

    @Override
    public void sendOaMessage(String userId, DingTalkOaMessage message) {
        System.out.printf(
            "[Mock DingTalk] send message to=%s title=%s url=%s content=%s forms=%s%n",
            userId,
            message.getTitle(),
            message.getActionUrl(),
            message.getContent(),
            message.getForms()
        );
    }
}
