package com.tianji.learning.domain.query;

import com.tianji.common.domain.query.PageQuery;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel(description = "用户端学习笔记分页查询条件")
public class NotePageQuery extends PageQuery {

    @ApiModelProperty("课程id")
    private Long courseId;

    @ApiModelProperty("小节id")
    private Long sectionId;

    @ApiModelProperty("是否只查询我的笔记")
    private Boolean onlyMine = false;
}
