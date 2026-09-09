package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.NoteFormDTO;
import com.tianji.learning.domain.dto.NoteUpdateDTO;
import com.tianji.learning.domain.po.Note;
import com.tianji.learning.domain.query.NotePageQuery;
import com.tianji.learning.domain.vo.NoteVO;
import com.tianji.learning.mapper.NoteMapper;
import com.tianji.learning.service.INoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 学习笔记表 服务实现类
 * </p>
 *
 * @author ThirteenAsh
 * @since 2026-09-09
 */
@Service
@SuppressWarnings("unchecked")
@RequiredArgsConstructor
public class NoteServiceImpl extends ServiceImpl<NoteMapper, Note> implements INoteService {

    private final UserClient userClient;

    /**
     * 保存笔记
     *
     * @param noteDTO 笔记表单数据
     */
    @Override
    public void saveNote(NoteFormDTO noteDTO) {
        // 1.获取当前登录用户
        Long userId = UserContext.getUser();

        // 2.将表单数据转换为笔记实体
        Note note = BeanUtils.copyBean(noteDTO, Note.class);

        // 3.补充作者、所属用户及默认状态
        note.setUserId(userId);
        note.setAuthorId(userId);
        note.setHidden(false);
        note.setIsGathered(false);
        note.setLikedTimes(0);

        // 4.保存笔记
        save(note);
    }

    /**
     * 采集一条公开笔记。
     * <p>采集会复制原笔记，并将副本设置为当前用户的私密笔记。</p>
     *
     * @param id 要采集的原笔记id
     */
    @Override
    @Transactional
    public void gatherNote(Long id) {
        // 1.获取当前登录用户
        Long userId = UserContext.getUser();

        // 2.查询原笔记，并判断笔记是否允许采集
        Note source = getById(id);
        if (source == null
                || Boolean.TRUE.equals(source.getIsPrivate())
                || Boolean.TRUE.equals(source.getHidden())
                || Boolean.TRUE.equals(source.getIsGathered())) {
            throw new BadRequestException("笔记不存在");
        }
        if (userId.equals(source.getUserId())) {
            throw new BadRequestException("不能采集自己的笔记");
        }

        // 3.判断当前用户是否已经采集过该笔记
        boolean gathered = lambdaQuery()
                .eq(Note::getUserId, userId)
                .eq(Note::getGatheredNoteId, id)
                .eq(Note::getIsGathered, true)
                .count() > 0;
        if (gathered) {
            return;
        }

        // 4.复制原笔记，并设置采集副本信息
        Note note = getNote(userId, source);

        // 5.保存采集副本
        save(note);
    }

    private static Note getNote(Long userId, Note source) {
        Note copy = new Note();
        copy.setUserId(userId);
        copy.setAuthorId(source.getAuthorId());
        copy.setCourseId(source.getCourseId());
        copy.setChapterId(source.getChapterId());
        copy.setSectionId(source.getSectionId());
        copy.setNoteMoment(source.getNoteMoment());
        copy.setContent(source.getContent());
        copy.setIsPrivate(true);
        copy.setHidden(false);
        copy.setGatheredNoteId(source.getId());
        copy.setIsGathered(true);
        copy.setLikedTimes(0);
        return copy;
    }

    /**
     * 取消采集一条笔记。
     *
     * @param id 要取消采集的原笔记id
     */
    @Override
    public void removeGatherNote(Long id) {
        // 1.获取当前登录用户
        Long userId = UserContext.getUser();

        // 2.删除当前用户基于原笔记创建的采集副本
        lambdaUpdate()
                .eq(Note::getUserId, userId)
                .eq(Note::getGatheredNoteId, id)
                .eq(Note::getIsGathered, true)
                .remove();
    }

    /**
     * 更新笔记
     *
     * @param id      笔记id
     * @param noteDTO 笔记更新数据
     */
    @Override
    public void updateNote(Long id, NoteUpdateDTO noteDTO) {
        // 1.获取当前登录用户
        Long userId = UserContext.getUser();

        // 2.查询笔记并判断是否属于当前用户
        Note note = getById(id);
        if (note == null || !userId.equals(note.getUserId())) {
            throw new BadRequestException("笔记不存在");
        }

        // 3.封装允许修改的字段
        Note update = new Note();
        update.setId(id);
        update.setContent(noteDTO.getContent());
        // 采集的笔记只能保持私密状态
        update.setIsPrivate(Boolean.TRUE.equals(note.getIsGathered()) || noteDTO.getIsPrivate());

        // 4.更新笔记
        updateById(update);
    }

    /**
     * 删除我的笔记
     *
     * @param id 笔记id
     */
    @Override
    public void removeMyNote(Long id) {
        // 1.获取当前登录用户
        Long userId = UserContext.getUser();

        // 2.查询笔记
        Note note = getById(id);
        if (note == null) {
            return;
        }

        // 3.判断笔记是否属于当前用户
        if (!userId.equals(note.getUserId())) {
            throw new BadRequestException("无权删除他人笔记");
        }

        // 4.删除笔记
        removeById(id);
    }

    /**
     * 分页查询笔记
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Override
    public PageDTO<NoteVO> queryNotePage(NotePageQuery query) {
        // 1.获取课程和小节查询条件
        Long courseId = query.getCourseId();
        Long sectionId = query.getSectionId();

        // 2.校验课程id和小节id不能同时为空
        if (courseId == null && sectionId == null) {
            throw new BadRequestException("课程id和小节id不能都为空");
        }

        // 3.获取当前登录用户及查询范围
        Long userId = UserContext.getUser();
        boolean onlyMine = Boolean.TRUE.equals(query.getOnlyMine());

        // 4.分页查询笔记
        // 4.1.查询我的笔记时，包含本人创建和采集的笔记
        // 4.2.查询全部笔记时，排除采集副本，并展示公开笔记及本人的私密原创笔记
        Page<Note> page = lambdaQuery()
                .eq(Note::getHidden, false)
                .eq(courseId != null, Note::getCourseId, courseId)
                .eq(sectionId != null, Note::getSectionId, sectionId)
                .eq(onlyMine, Note::getUserId, userId)
                .eq(!onlyMine, Note::getIsGathered, false)
                .and(!onlyMine, wrapper -> wrapper
                        .eq(Note::getIsPrivate, false)
                        .or()
                        .eq(Note::getUserId, userId))
                .orderByAsc(sectionId != null, Note::getNoteMoment)
                .orderByDesc(sectionId == null, Note::getCreateTime)
                .page(new Page<>(query.getPageNo(), query.getPageSize()));

        // 5.判断分页结果是否为空
        List<Note> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }

        // 6.查询当前用户已经采集的原笔记id
        Set<Long> gatheredNoteIds = new HashSet<>();
        if (!onlyMine) {
            Set<Long> noteIds = records.stream()
                    .map(Note::getId)
                    .collect(Collectors.toSet());
            gatheredNoteIds = lambdaQuery()
                    .select(Note::getGatheredNoteId)
                    .eq(Note::getUserId, userId)
                    .eq(Note::getIsGathered, true)
                    .in(Note::getGatheredNoteId, noteIds)
                    .list()
                    .stream()
                    .map(Note::getGatheredNoteId)
                    .collect(Collectors.toSet());
        }

        // 7.收集作者id并批量查询用户信息
        Set<Long> authorIds = records.stream()
                .map(Note::getAuthorId)
                .collect(Collectors.toCollection(HashSet::new));
        List<UserDTO> users = userClient.queryUserByIds(authorIds);

        // 8.将用户集合转换为以用户id为键的Map
        Map<Long, UserDTO> userMap = new HashMap<>(authorIds.size());
        if (CollUtils.isNotEmpty(users)) {
            userMap = users.stream().collect(Collectors.toMap(
                    UserDTO::getId,
                    user -> user,
                    (left, right) -> left));
        }

        // 9.封装笔记分页结果
        List<NoteVO> list = new ArrayList<>(records.size());
        for (Note note : records) {
            // 9.1.将笔记实体转换为VO
            NoteVO vo = BeanUtils.copyBean(note, NoteVO.class);

            // 9.2.查询全部笔记时，补充当前用户是否已经采集
            if (!onlyMine) {
                vo.setIsGathered(gatheredNoteIds.contains(note.getId()));
            }

            // 9.3.补充作者昵称和头像
            UserDTO author = userMap.get(note.getAuthorId());
            if (author != null) {
                vo.setAuthorName(author.getName());
                vo.setAuthorIcon(author.getIcon());
            }
            list.add(vo);
        }

        // 10.返回分页结果
        return PageDTO.of(page, list);
    }
}
