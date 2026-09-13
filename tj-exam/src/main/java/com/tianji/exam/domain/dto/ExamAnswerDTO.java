package com.tianji.exam.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
@ApiModel(description = "学员的单题答案")
public class ExamAnswerDTO {

    @NotNull(message = "题目id不能为空")
    @ApiModelProperty("题目id")
    private Long questionId;

    @NotNull(message = "题目类型不能为空")
    @ApiModelProperty("题目类型")
    private Integer questionType;

    @ApiModelProperty("学员答案，未作答时可为空")
    private String answer;
}
