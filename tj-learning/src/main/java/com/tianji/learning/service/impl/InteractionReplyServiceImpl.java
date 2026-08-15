package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.remark.RemarkClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.tianji.common.constants.Constant.DATA_FIELD_NAME_CREATE_TIME;
import static com.tianji.common.constants.Constant.DATA_FIELD_NAME_LIKED_TIME;

/**
 * 互动问答回复服务实现。
 * <p>
 * 支持对问题发布回答以及对回答继续评论，维护问题的回答数、最近回答和待审核状态。
 * 学员回答问题后在事务提交时异步发放积分；查询时遵守匿名和隐藏规则，并补充用户与点赞信息。
 * </p>
 *
 * @author ThirteenAsh
 * @since 2026-08-11
 */
@Service
@RequiredArgsConstructor
public class InteractionReplyServiceImpl extends ServiceImpl<InteractionReplyMapper, InteractionReply> implements IInteractionReplyService {

    private final IInteractionQuestionService questionService;
    private final UserClient userClient;
    private final RemarkClient remarkClient;
    private final RabbitMqHelper mqHelper;

    /**
     * 发布当前登录用户对问题的回答或对已有回答的评论。
     * <p>新增回答会更新问题的回答数和最近回答；学员回答问题后，在事务提交时异步发送积分消息。</p>
     *
     * @param replyDTO 回复内容、所属问题及目标回答等数据
     */
    @Override
    @Transactional
    public void saveReply(ReplyDTO replyDTO) {
        boolean isStudent = Boolean.TRUE.equals(replyDTO.getIsStudent());
        // 1.获取登录用户
        Long userId = UserContext.getUser();
        // 2.新增回答
        InteractionReply reply = BeanUtils.toBean(replyDTO, InteractionReply.class);
        reply.setUserId(userId);
        save(reply);
        // 3.累加评论数或者累加回答数
        // 3.1.判断当前回复的类型是否是回答
        boolean isAnswer = replyDTO.getAnswerId() == null;
        // 回答一个问题，发送积分消息，在事务提交后发送
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (isAnswer && isStudent) {
                    mqHelper.send(
                            MqConstants.Exchange.LEARNING_EXCHANGE,
                            MqConstants.Key.WRITE_REPLY,
                            userId);
                }
            }
        });
        if (!isAnswer) {
            // 3.2.是评论，则需要更新上级回答的评论数量
            lambdaUpdate()
                    .setSql("reply_times = reply_times + 1")
                    .eq(InteractionReply::getId, replyDTO.getAnswerId())
                    .update();
        }

        // 老师评论时，问题表无字段需要更新
        if (!isAnswer && !isStudent) {
            return;
        }

        // 3.3.尝试更新问题表中的状态、 最近一次回答、回答数量
        questionService.lambdaUpdate()
                .set(isAnswer, InteractionQuestion::getLatestAnswerId, reply.getId())
                .setSql(isAnswer, "answer_times = answer_times + 1")
                .set(replyDTO.getIsStudent(), InteractionQuestion::getStatus, QuestionStatus.UN_CHECK.getValue())
                .eq(InteractionQuestion::getId, replyDTO.getQuestionId())
                .update();

    }

    /**
     * 分页查询问题下的回答或回答下的评论，并补充用户、目标用户和点赞状态。
     * <p>普通查询会过滤隐藏内容并保护匿名身份，运营端查询可查看完整信息。</p>
     *
     * @param query 问题或回答编号及分页条件，两者至少提供一个
     * @param forAdmin 是否按运营端权限查询
     * @return 回复分页结果
     */
    @Override
    public PageDTO<ReplyVO> queryReplyPage(ReplyPageQuery query, boolean forAdmin) {
        // 1.问题id和回答id至少要有一个，先做参数判断
        Long questionId = query.getQuestionId();
        Long answerId = query.getAnswerId();
        if (questionId == null && answerId == null) {
            throw new BadRequestException("问题或回答id不能都为空");
        }
        // 标记当前是查询问题下的回答
        boolean isQueryAnswer = questionId != null;
        // 2.分页查询reply
        Page<InteractionReply> page = lambdaQuery()
                .eq(isQueryAnswer, InteractionReply::getQuestionId, questionId)
                .eq(InteractionReply::getAnswerId, isQueryAnswer ? 0L : answerId)
                .eq(!forAdmin, InteractionReply::getHidden, false)
                .page(query.toMpPage( // 先根据点赞数排序，点赞数相同，再按照创建时间排序
                        new OrderItem(DATA_FIELD_NAME_LIKED_TIME, false),
                        new OrderItem(DATA_FIELD_NAME_CREATE_TIME, true))
                );
        List<InteractionReply> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        // 3.数据处理，需要查询：提问者信息、回复目标信息、当前用户是否点赞
        Set<Long> userIds = new HashSet<>();
        Set<Long> answerIds = new HashSet<>();
        Set<Long> targetReplyIds = new HashSet<>();
        // 3.1.获取提问者id 、回复的目标id、当前回答或评论id（统计点赞信息）
        for (InteractionReply r : records) {
            if(!r.getAnonymity() || forAdmin) {
                // 非匿名
                userIds.add(r.getUserId());
            }
            targetReplyIds.add(r.getTargetReplyId());
            answerIds.add(r.getId());
        }
        // 3.2.查询目标回复，如果目标回复不是匿名，则需要查询出目标回复的用户信息
        targetReplyIds.remove(0L);
        targetReplyIds.remove(null);
        if(!targetReplyIds.isEmpty()) {
            List<InteractionReply> targetReplies = listByIds(targetReplyIds);
            Set<Long> targetUserIds = targetReplies.stream()
                    // 过滤掉匿名的目标回复，除非当前是管理员查询
                    .filter(Predicate.not(InteractionReply::getAnonymity).or(r -> forAdmin))
                    .map(InteractionReply::getUserId)
                    .collect(Collectors.toSet());
            userIds.addAll(targetUserIds);
        }
        // 3.3.查询用户
        Map<Long, UserDTO> userMap = new HashMap<>(userIds.size());
        if(!userIds.isEmpty()) {
            List<UserDTO> users = userClient.queryUserByIds(userIds);
            userMap = users.stream().collect(Collectors.toMap(UserDTO::getId, u -> u));
        }
        // 3.4. 查询用户点赞状态
        Set<Long> bizLiked = remarkClient.isBizLiked(answerIds);
        // 4.处理VO
        List<ReplyVO> list = new ArrayList<>(records.size());
        for (InteractionReply r : records) {
            // 4.1.拷贝基础属性
            ReplyVO v = BeanUtils.toBean(r, ReplyVO.class);
            list.add(v);
            // 4.2.回复人信息
            if(!r.getAnonymity() || forAdmin){
                UserDTO userDTO = userMap.get(r.getUserId());
                if (userDTO != null) {
                    v.setUserIcon(userDTO.getIcon());
                    v.setUserName(userDTO.getName());
                    v.setUserType(userDTO.getType());
                }
            }
            // 4.3.如果存在评论的目标，则需要设置目标用户信息
            if(r.getTargetReplyId() != null){
                UserDTO targetUser = userMap.get(r.getTargetUserId());
                if (targetUser != null) {
                    v.setTargetUserName(targetUser.getName());
                }
            }
            // 4.4.4 点赞状态
            v.setLiked(bizLiked.contains(r.getId()));
        }
        return new PageDTO<>(page.getTotal(), page.getPages(), list);
    }

    /**
     * 设置回答或评论的隐藏状态；隐藏回答时同步处理其直属评论。
     *
     * @param id 回答或评论编号
     * @param hidden 是否隐藏
     */
    @Override
    @Transactional
    public void hiddenReply(Long id, Boolean hidden) {
        // 1.查询
        InteractionReply old = getById(id);
        if (old == null) {
            return;
        }

        // 2.隐藏回答
        InteractionReply reply = new InteractionReply();
        reply.setId(id);
        reply.setHidden(hidden);
        updateById(reply);

        // 3.隐藏评论，先判断是否是回答，回答才需要隐藏下属评论
        if (old.getAnswerId() != null && old.getAnswerId() != 0) {
            // 3.1.有answerId，说明自己是评论，无需处理
            return;
        }
        // 3.2.没有answerId，说明自己是回答，需要隐藏回答下的评论
        lambdaUpdate()
                .set(InteractionReply::getHidden, hidden)
                .eq(InteractionReply::getAnswerId, id)
                .update();
    }

    /**
     * 查询单条回复详情，并补充回复人、被回复人及当前用户点赞状态。
     *
     * @param id 回复编号
     * @return 回复详情
     */
    @Override
    public ReplyVO queryReplyById(Long id) {
        // 1.根据id查询
        InteractionReply r = getById(id);
        // 2.数据处理，需要查询用户信息、评论目标信息、当前用户是否点赞
        Set<Long> userIds = new HashSet<>();
        // 2.1.获取用户 id
        userIds.add(r.getUserId());
        // 2.2.查询评论目标，如果评论目标不是匿名，则需要查询出目标回复的用户id
        if(r.getTargetReplyId() != null && r.getTargetReplyId() != 0) {
            InteractionReply target = getById(r.getTargetReplyId());
            if(!target.getAnonymity()) {
                userIds.add(target.getUserId());
            }
        }
        // 2.3.查询用户详细
        Map<Long, UserDTO> userMap = new HashMap<>(userIds.size());
        List<UserDTO> users = userClient.queryUserByIds(userIds);
        userMap = users.stream().collect(Collectors.toMap(UserDTO::getId, u -> u));
        // 2.4. 查询用户点赞状态
        Set<Long> bizLiked = remarkClient.isBizLiked(CollUtils.singletonList(id));
        // 4.处理VO
        // 4.1.拷贝基础属性
        ReplyVO v = BeanUtils.toBean(r, ReplyVO.class);
        // 4.2.回复人信息
        UserDTO userDTO = userMap.get(r.getUserId());
        if (userDTO != null) {
            v.setUserIcon(userDTO.getIcon());
            v.setUserName(userDTO.getName());
            v.setUserType(userDTO.getType());
        }
        // 4.3.目标用户
        UserDTO targetUser = userMap.get(r.getTargetUserId());
        if (targetUser != null) {
            v.setTargetUserName(targetUser.getName());
        }
        // 4.4. 点赞状态
        v.setLiked(bizLiked.contains(id));
        return v;
    }
}
