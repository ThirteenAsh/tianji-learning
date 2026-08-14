package com.tianji.learning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.learning.domain.po.LearningLesson;

/**
 * <p>
 * 学生课程表 Mapper 接口
 * </p>
 *
 * @author author
 * @since 2026-08-05
 */
public interface LearningLessonMapper extends BaseMapper<LearningLesson> {

    Integer queryTotalPlan(Long userId);
}
