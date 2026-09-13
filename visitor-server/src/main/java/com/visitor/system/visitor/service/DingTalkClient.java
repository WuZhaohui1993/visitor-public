package com.visitor.system.visitor.service;

import com.visitor.system.visitor.dto.DingTalkUserDto;
import com.visitor.system.visitor.dto.DingTalkOaMessage;

import java.util.List;

public interface DingTalkClient {

    List<DingTalkUserDto> searchUsers(String keyword);

    DingTalkUserDto getUserByAuthCode(String authCode);

    void sendOaMessage(String userId, DingTalkOaMessage message);
}
