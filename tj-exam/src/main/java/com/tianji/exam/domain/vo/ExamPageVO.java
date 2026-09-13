package com.tianji.exam.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@ApiModel(description = "学员练习或考试记录")
public class ExamPageVO {

    @ApiModelProperty("考试记录id")
    private String id;

    @ApiModelProperty("类型：1-练习，2-考试")
    private Integer type;

    @ApiModelProperty("得分")
    private Integer score;

    @ApiModelProperty("交卷时间")
    private LocalDateTime commitTime;

    @ApiModelProperty("用时，单位：秒")
    private Integer duration;

    @ApiModelProperty("课程名称")
    private String courseName;

    @ApiModelProperty("小节名称")
    private String sectionName;
}
