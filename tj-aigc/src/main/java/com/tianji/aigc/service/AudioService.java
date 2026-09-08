package com.tianji.aigc.service;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

public interface AudioService {
    ResponseBodyEmitter ttsStream(String text);

    String stt(MultipartFile audioFile);
}
