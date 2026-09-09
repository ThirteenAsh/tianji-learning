package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.NoteFormDTO;
import com.tianji.learning.domain.dto.NoteUpdateDTO;
import com.tianji.learning.domain.po.Note;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.query.NotePageQuery;
import com.tianji.learning.domain.vo.NoteVO;

/**
 * <p>
 * 学习笔记表 服务类
 * </p>
 *
 * @author ThirteenAsh
 * @since 2026-09-09
 */
public interface INoteService extends IService<Note> {

    void saveNote(NoteFormDTO noteDTO);

    void gatherNote(Long id);

    void removeGatherNote(Long id);

    void updateNote(Long id, NoteUpdateDTO noteDTO);

    void removeMyNote(Long id);

    PageDTO<NoteVO> queryNotePage(NotePageQuery query);
}
