package com.tianji.learning.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.learning.domain.po.Note;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Set;

/**
 * <p>
 * 学习笔记表 Mapper 接口
 * </p>
 *
 * @author ThirteenAsh
 * @since 2026-09-09
 */
public interface NoteMapper extends BaseMapper<Note> {

    Page<Note> queryNotePageForAdmin(
            Page<Note> page,
            @Param("ew") QueryWrapper<Note> wrapper);

    @Select("SELECT user_id FROM note WHERE gathered_note_id = #{id} AND is_gathered = 1")
    Set<Long> queryNoteGathers(@Param("id") Long id);
}
