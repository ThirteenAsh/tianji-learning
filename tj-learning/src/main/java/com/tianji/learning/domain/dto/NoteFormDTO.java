package com.tianji.learning.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;

@Data
@ApiModel(description = "新增学习笔记表单")
public class NoteFormDTO {

    @NotBlank(message = "笔记内容不能为空")
    @ApiModelProperty("笔记内容")
    private String content;

    @NotNull(message = "隐私标记不能为空")
    @ApiModelProperty("是否是私密笔记")
    private Boolean isPrivate;

    @NotNull(message = "笔记时间不能为空")
    @PositiveOrZero(message = "笔记时间不能小于0")
    @ApiModelProperty("记录笔记时的视频播放时间，单位：秒")
    private Integer noteMoment;

    @NotNull(message = "课程id不能为空")
    @ApiModelProperty("课程id")
    private Long courseId;

    @NotNull(message = "章id不能为空")
    @ApiModelProperty("章id")
    private Long chapterId;

    @NotNull(message = "小节id不能为空")
    @ApiModelProperty("小节id")
    private Long sectionId;
}
