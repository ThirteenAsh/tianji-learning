package com.tianji.learning.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 学习笔记表
 * </p>
 *
 * @author ThirteenAsh
 * @since 2026-09-09
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("note")
@ApiModel(value="Note对象", description="学习笔记表")
public class Note implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "笔记ID")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    @ApiModelProperty(value = "笔记所属用户ID；采集笔记时为采集人ID")
    private Long userId;

    @ApiModelProperty(value = "原始作者ID")
    private Long authorId;

    @ApiModelProperty(value = "课程ID")
    private Long courseId;

    @ApiModelProperty(value = "章ID")
    private Long chapterId;

    @ApiModelProperty(value = "小节ID")
    private Long sectionId;

    @ApiModelProperty(value = "记录笔记时的视频播放时间，单位：秒")
    private Integer noteMoment;

    @ApiModelProperty(value = "笔记内容")
    private String content;

    @ApiModelProperty(value = "是否私密：0-公开，1-私密")
    private Boolean isPrivate;

    @ApiModelProperty(value = "是否被管理端隐藏：0-显示，1-隐藏")
    private Boolean hidden;

    @ApiModelProperty(value = "隐藏原因，当前接口可暂不使用")
    private String hiddenReason;

    @ApiModelProperty(value = "采集来源的原笔记ID；原创笔记为NULL")
    private Long gatheredNoteId;

    @ApiModelProperty(value = "是否为采集副本：0-原创，1-采集")
    private Boolean isGathered;

    @ApiModelProperty(value = "点赞次数")
    private Integer likedTimes;

    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间")
    private LocalDateTime updateTime;


}
