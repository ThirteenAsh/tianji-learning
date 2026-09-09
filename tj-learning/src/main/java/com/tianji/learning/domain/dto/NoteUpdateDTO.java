package com.tianji.learning.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@ApiModel(description = "修改学习笔记表单")
public class NoteUpdateDTO {

    @NotBlank(message = "笔记内容不能为空")
    @ApiModelProperty("笔记内容")
    private String content;

    @NotNull(message = "隐私标记不能为空")
    @ApiModelProperty("是否是私密笔记")
    private Boolean isPrivate;
}
