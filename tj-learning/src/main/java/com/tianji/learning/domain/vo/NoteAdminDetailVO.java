package com.tianji.learning.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@ApiModel(description = "管理端学习笔记详情")
public class NoteAdminDetailVO {

    @ApiModelProperty("笔记id")
    private Long id;

    @ApiModelProperty("课程名称")
    private String courseName;

    @ApiModelProperty("课程分类名称，以/拼接")
    private String categoryNames;

    @ApiModelProperty("章名称")
    private String chapterName;

    @ApiModelProperty("节名称")
    private String sectionName;

    @ApiModelProperty("笔记内容")
    private String content;

    @ApiModelProperty("记录笔记时的视频播放时间，单位：秒")
    private Integer noteMoment;

    @ApiModelProperty("被采集次数")
    private Integer usedTimes;

    @ApiModelProperty("点赞次数")
    private Integer likedTimes;

    @ApiModelProperty("是否被隐藏")
    private Boolean hidden;

    @ApiModelProperty("作者名称")
    private String authorName;

    @ApiModelProperty("作者手机号")
    private String authorPhone;

    @ApiModelProperty("发布时间")
    private LocalDateTime createTime;

    @ApiModelProperty("采集用户名称集合")
    private List<String> gathers;
}
