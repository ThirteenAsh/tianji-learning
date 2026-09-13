package com.tianji.exam.domain.dto;

import com.tianji.common.validate.annotations.EnumValid;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
@ApiModel(description = "开始练习或考试的请求")
public class ExamStartDTO {

    @NotNull(message = "课程id不能为空")
    @ApiModelProperty("课程id")
    private Long courseId;

    @NotNull(message = "小节id不能为空")
    @ApiModelProperty("小节id")
    private Long sectionId;

    @NotNull(message = "考试类型不能为空")
    @EnumValid(enumeration = {1, 2}, message = "考试类型只能是1或2")
    @ApiModelProperty("类型：1-练习，2-考试")
    private Integer type;
}
