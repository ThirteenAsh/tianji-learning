package com.tianji.aigc.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.aigc.entity.ChatRecord;
import com.tianji.aigc.mapper.ChatRecordMapper;
import com.tianji.aigc.service.ChatRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ChatRecordServiceImpl extends ServiceImpl<ChatRecordMapper, ChatRecord> implements ChatRecordService {
}
