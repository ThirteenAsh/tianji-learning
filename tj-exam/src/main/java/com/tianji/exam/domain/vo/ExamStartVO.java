package com.tianji.exam.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

@Data
@ApiModel(description = "开始考试的结果")
public class ExamStartVO {

    @ApiModelProperty("考试记录id，交卷时使用")
    private String id;

    @ApiModelProperty("考试题目列表")
    private List<ExamQuestionVO> questions;
}
