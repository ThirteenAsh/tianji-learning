package com.tianji.learning.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@ApiModel(description = "管理端学习笔记分页信息")
public class NoteAdminVO {

    @ApiModelProperty("笔记id")
    private Long id;

    @ApiModelProperty("课程名称")
    private String courseName;

    @ApiModelProperty("章名称")
    private String chapterName;

    @ApiModelProperty("节名称")
    private String sectionName;

    @ApiModelProperty("笔记内容")
    private String content;

    @ApiModelProperty("是否被隐藏")
    private Boolean hidden;

    @ApiModelProperty("被采集次数")
    private Integer usedTimes;

    @ApiModelProperty("点赞次数")
    private Integer likedTimes;

    @ApiModelProperty("作者名称")
    private String authorName;

    @ApiModelProperty("发布时间")
    private LocalDateTime createTime;
}
