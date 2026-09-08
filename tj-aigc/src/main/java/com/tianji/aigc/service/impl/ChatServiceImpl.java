package com.tianji.aigc.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.api.DashScopeAgentApi;
import com.tianji.aigc.config.SystemPromptConfig;
import com.tianji.aigc.config.ToolResultHolder;
import com.tianji.aigc.constants.Constant;
import com.tianji.aigc.enums.ChatEventTypeEnum;
import com.tianji.aigc.service.ChatService;
import com.tianji.aigc.service.ChatSessionService;
import com.tianji.aigc.vo.ChatEventVO;
import com.tianji.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "tj.ai", name = "chat-type", havingValue = "ENHANCE")
public class ChatServiceImpl implements ChatService {

    // 停止事件对象，表示大模型生成结束
    private static final ChatEventVO STOP_EVENT = ChatEventVO.builder().eventType(ChatEventTypeEnum.STOP.getValue()).build();
    public final ChatClient chatClient;
    public final SystemPromptConfig systemPromptConfig;
    private final ChatMemory chatMemory;

    // 存储大模型的生成状态，这里采用ConcurrentHashMap是确保线程安全
    // 目前的版本暂时用Map实现，如果考虑分布式环境的话，可以考虑用redis来实现
    private static final Map<String, Boolean> GENERATE_STATUS = new ConcurrentHashMap<>();
    private final VectorStore vectorStore;

    private final ChatSessionService chatSessionService;
    private final ChatClient openAiChatClient;


    @Override
    public Flux<ChatEventVO> chat(String question, String sessionId) {
        // 获取对话id
        var conversationId = ChatService.getConversationId(sessionId);
        var outputBuilder = new StringBuilder();

        //生成工具调用的请求id
        String requestId = IdUtil.simpleUUID();
        // 获取用户id
        var userId = UserContext.getUser();

        // 创建RAG增强
        var qaAdvisor = QuestionAnswerAdvisor.builder(this.vectorStore)
                .searchRequest(SearchRequest.builder()
                        .similarityThreshold(0.6d) // 相似度阈值
                        .topK(6) // 搜索条数
                        .build())
                .build();

        // 异步更新会话信息
        this.chatSessionService.update(sessionId, question, userId);

        return chatClient.prompt()
                .system(promptSystem -> promptSystem
                        //系统提示词
                        .text(systemPromptConfig.getChatSystemMessage().get())
                        //系统提示中的参数
                        .params(Map.of("now", DateUtil.now()))
                )
                .advisors(advisor -> advisor
                        .advisors(qaAdvisor) //设置RAG增强
                        .param(ChatMemory.CONVERSATION_ID, conversationId)) //设置对话记忆中的对话id
                .toolContext(Map.of(Constant.REQUEST_ID, requestId, Constant.USER_ID, userId)) //通过工具上下文传递参数
                .user(question)
                .stream()
                .chatResponse()
                .doFirst(() -> {
                    //设置大模型生成状态为true，表示正在生成
                    GENERATE_STATUS.put(sessionId, true);
                })
                .doOnError(error -> {
                    log.error("大模型生成异常", error);
                    GENERATE_STATUS.remove(sessionId);
                })
                .doOnComplete(() -> GENERATE_STATUS.remove(sessionId))
                .doOnCancel(() -> {
                    // 当输出被取消时，保存输出的内容到历史记录中
                    this.saveStopHistoryRecord(conversationId, outputBuilder.toString());
                })
                //当大模型生成状态为false时，停止生成
                .takeWhile(chatResponse -> GENERATE_STATUS.getOrDefault(sessionId, false))
                .map(chatResponse -> {
                    //此段逻辑是为了可以根据消息di获取的请求id，再通过请求id就可以获取到参数列表，存储到ToolResultHolder中
                    //获取消息id
                    String messageId = chatResponse.getMetadata().getId();
                    ToolResultHolder.put(messageId, Constant.REQUEST_ID, requestId);
                    //拿到大模型生成的内容
                    var text = chatResponse.getResult().getOutput().getText();
                    //大模型输出的内容
                    outputBuilder.append(text);
                    return ChatEventVO.builder()
                            .eventData(text)
                            .eventType(ChatEventTypeEnum.DATA.getValue())
                            .build();
                })
                //如果当前请求中有工具生成的数据（也就是工具被调用了），就加到流中，反之不添加
                .concatWith(Flux.defer(() -> {
                    // 通过请求id获取到参数列表，如果不为空，就将其追加到返回结果中
                    var map = ToolResultHolder.get(requestId);
                    if (CollUtil.isNotEmpty(map)) {
                        ToolResultHolder.remove(requestId); // 清除参数列表

                        // 响应给前端的参数数据
                        var chatEventVO = ChatEventVO.builder()
                                .eventData(map)
                                .eventType(ChatEventTypeEnum.PARAM.getValue())
                                .build();
                        return Flux.just(chatEventVO, STOP_EVENT);
                    }
                    return Flux.just(STOP_EVENT);
                }));
    }

    @Override
    public void stop(String sessionId) {
        GENERATE_STATUS.remove(sessionId);
    }

    @Override
    public String chatText(String question) {
        return this.openAiChatClient.prompt()
                .system(promptSystem -> promptSystem.text(this.systemPromptConfig.getTextSystemMessage().get()))
                .user(question)
                .call()
                .content();
    }

    /**
     * 保存停止输出的记录
     *
     * @param conversationId 会话id
     * @param content        大模型输出的内容
     */
    private void saveStopHistoryRecord(String conversationId, String content) {
        this.chatMemory.add(conversationId, new AssistantMessage(content));
    }
}
