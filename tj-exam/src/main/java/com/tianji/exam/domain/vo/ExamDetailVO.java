package com.tianji.exam.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(description = "考试记录中的单题作答详情")
public class ExamDetailVO {

    @ApiModelProperty("学员答案")
    private String answer;

    @ApiModelProperty("老师评语；客观题暂无评语")
    private String comment;

    @ApiModelProperty("是否正确")
    private Boolean correct;

    @ApiModelProperty("学员得分")
    private Integer score;

    @ApiModelProperty("题目快照，包含正确答案和解析")
    private ExamQuestionDetailVO question;
}
