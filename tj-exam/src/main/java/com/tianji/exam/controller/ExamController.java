package com.tianji.exam.controller;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.exam.domain.dto.ExamStartDTO;
import com.tianji.exam.domain.dto.ExamSubmitDTO;
import com.tianji.exam.domain.vo.ExamDetailVO;
import com.tianji.exam.domain.vo.ExamPageVO;
import com.tianji.exam.domain.vo.ExamStartVO;
import com.tianji.exam.service.IExamService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import javax.validation.Valid;
import java.util.List;

@Api(tags = "学员考试接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/exams")
public class ExamController {

    private final IExamService examService;

    @ApiOperation("获取题目并开始练习或考试")
    @PostMapping
    public ExamStartVO startExam(@Valid @RequestBody ExamStartDTO form) {
        return examService.startExam(form);
    }

    @ApiOperation("提交练习或考试答案")
    @PostMapping("/details")
    public void submitExam(@Valid @RequestBody ExamSubmitDTO form) {
        examService.submitExam(form);
    }

    @ApiOperation("分页查询我的考试记录")
    @GetMapping("/page")
    public PageDTO<ExamPageVO> queryMyExamPage(@Valid PageQuery query) {
        return examService.queryMyExamPage(query);
    }

    @ApiOperation("查询我的考试记录详情")
    @GetMapping("/{id}")
    public List<ExamDetailVO> queryMyExamDetails(@PathVariable("id") String id) {
        return examService.queryMyExamDetails(id);
    }
}
