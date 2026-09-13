package com.tianji.exam.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

@Data
@ApiModel(description = "已交卷试题详情")
public class ExamQuestionDetailVO {

    @ApiModelProperty("题目id")
    private Long id;

    @ApiModelProperty("题目名称")
    private String name;

    @ApiModelProperty("题目类型")
    private Integer type;

    @ApiModelProperty("题目分值")
    private Integer score;

    @ApiModelProperty("选择题选项")
    private List<String> options;

    @ApiModelProperty("正确答案")
    private String answer;

    @ApiModelProperty("答案解析")
    private String analysis;

    @ApiModelProperty("难易度")
    private Integer difficulty;
}
