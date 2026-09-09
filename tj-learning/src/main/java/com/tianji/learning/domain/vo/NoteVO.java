package com.tianji.learning.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@ApiModel(description = "用户端学习笔记信息")
public class NoteVO {

    @ApiModelProperty("笔记id")
    private Long id;

    @ApiModelProperty("笔记内容")
    private String content;

    @ApiModelProperty("记录笔记时的视频播放时间，单位：秒")
    private Integer noteMoment;

    @ApiModelProperty("是否是私密笔记")
    private Boolean isPrivate;

    @ApiModelProperty("是否是采集的笔记")
    private Boolean isGathered;

    @ApiModelProperty("点赞次数")
    private Integer likedTimes;

    @ApiModelProperty("作者id")
    private Long authorId;

    @ApiModelProperty("作者名字")
    private String authorName;

    @ApiModelProperty("作者头像")
    private String authorIcon;

    @ApiModelProperty("笔记发布时间")
    private LocalDateTime createTime;
}
