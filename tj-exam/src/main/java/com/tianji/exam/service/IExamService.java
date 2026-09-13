package com.tianji.exam.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.exam.domain.dto.ExamStartDTO;
import com.tianji.exam.domain.dto.ExamSubmitDTO;
import com.tianji.exam.domain.vo.ExamDetailVO;
import com.tianji.exam.domain.vo.ExamPageVO;
import com.tianji.exam.domain.vo.ExamStartVO;

import java.util.List;

public interface IExamService {

    ExamStartVO startExam(ExamStartDTO form);

    void submitExam(ExamSubmitDTO form);

    PageDTO<ExamPageVO> queryMyExamPage(PageQuery query);

    List<ExamDetailVO> queryMyExamDetails(String id);
}
