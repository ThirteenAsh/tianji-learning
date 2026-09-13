package com.tianji.exam.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

@Data
@ApiModel(description = "考试中的题目，不包含正确答案")
public class ExamQuestionVO {

    @ApiModelProperty("题目id")
    private Long id;

    @ApiModelProperty("题目名称")
    private String name;

    @ApiModelProperty("题目类型")
    private String type;

    @ApiModelProperty("难易度")
    private Integer difficulty;

    @ApiModelProperty("本题分值")
    private Integer score;

    @ApiModelProperty("选择题选项")
    private List<String> options;
}
