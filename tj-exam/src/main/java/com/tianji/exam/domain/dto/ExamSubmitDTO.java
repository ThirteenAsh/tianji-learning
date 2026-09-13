package com.tianji.exam.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.List;

@Data
@ApiModel(description = "提交考试答案")
public class ExamSubmitDTO {

    @NotBlank(message = "考试记录id不能为空")
    @ApiModelProperty("考试记录id")
    private String id;

    @Valid
    @ApiModelProperty("学员已作答的题目；未作答题目可以不传")
    private List<ExamAnswerDTO> examDetails;
}
