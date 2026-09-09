package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.NoteFormDTO;
import com.tianji.learning.domain.dto.NoteUpdateDTO;
import com.tianji.learning.domain.query.NotePageQuery;
import com.tianji.learning.domain.vo.NoteVO;
import com.tianji.learning.service.INoteService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * <p>
 * 学习笔记表 前端控制器
 * </p>
 *
 * @author ThirteenAsh
 * @since 2026-09-09
 */
@RestController
@RequiredArgsConstructor
@Api(tags = "学习笔记表 前端控制器")
@RequestMapping("/notes")
public class NoteController {

    private final INoteService noteService;

    @ApiOperation("新增笔记")
    @PostMapping
    public void saveNote(@Valid @RequestBody NoteFormDTO noteDTO) {
        noteService.saveNote(noteDTO);
    }

    @ApiOperation("修改我的笔记")
    @PutMapping("/{id}")
    public void updateNote(
            @ApiParam(value = "笔记id", example = "1") @PathVariable("id") Long id,
            @Valid @RequestBody NoteUpdateDTO noteDTO) {
        noteService.updateNote(id, noteDTO);
    }

    @ApiOperation("删除我的笔记")
    @DeleteMapping("/{id}")
    public void removeMyNote(
            @ApiParam(value = "笔记id", example = "1") @PathVariable("id") Long id) {
        noteService.removeMyNote(id);
    }

    @ApiOperation("用户端分页查询笔记")
    @GetMapping("/page")
    public PageDTO<NoteVO> queryNotePage(@Valid NotePageQuery query) {
        return noteService.queryNotePage(query);
    }
}
