package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.IdAndNumDTO;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.*;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.domain.vo.LearningPlanVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 学生课程表与学习计划服务实现。
 * <p>
 * 管理用户已购课程的有效期、学习状态和最近学习位置，并聚合课程与目录信息提供“我的课程”展示。
 * 同时维护周学习频次计划，统计本周计划小节数及已完成小节数。
 * </p>
 *
 * @author author
 * @since 2026-08-05
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LearningLessonServiceImpl extends ServiceImpl<LearningLessonMapper, LearningLesson> implements ILearningLessonService {

    private final CourseClient courseClient;
    private final CatalogueClient catalogueClient;
    private final LearningRecordMapper recordMapper;

    /**
     * 将用户购买的课程批量加入课表，并按课程有效期设置过期时间。
     *
     * @param userId 购课用户编号
     * @param courseIds 已购买的课程编号集合
     */
    @Override
    @Transactional
    public void addUserLessons(Long userId, List<Long> courseIds) {
        //查询课程有效期
        List<CourseSimpleInfoDTO> classInfoList = courseClient.getSimpleInfoList(courseIds);
        if(CollUtils.isEmpty(classInfoList)){
            //课程不存在
            log.error("课程不存在");
            return;
        }
        //循环遍历，处理LearningLesson对象
        List<LearningLesson> list = new ArrayList<>(classInfoList.size());
        for (CourseSimpleInfoDTO course : classInfoList) {
            LearningLesson lesson = new LearningLesson();
            //获取过期的时间
            Integer validDuration = course.getValidDuration();
            if (validDuration != null && validDuration > 0) {
                LocalDateTime now = LocalDateTime.now();
                lesson.setCreateTime(now);
                lesson.setExpireTime(now.plusMonths(validDuration));
            }
            lesson.setUserId(userId);
            lesson.setCourseId(course.getId());
            list.add(lesson);
        }
        //批量新增
        saveBatch(list);
    }

    /**
     * 分页查询当前登录用户的课程，并补充课程名称、封面和小节总数。
     *
     * @param query 分页条件
     * @return 按最近学习时间倒序排列的个人课程页
     */
    @Override
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery query) {
        //获取当前登录用户
        Long userId = UserContext.getUser();
        //分页查询
        Page<LearningLesson> page = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .page(query.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = page.getRecords();
        if(CollUtils.isEmpty(records)){
            return PageDTO.empty(page);
        }

        Map<Long, CourseSimpleInfoDTO> cMap = queryCourseSimpleInfoList(records);

        //封装返回结果
        List<LearningLessonVO> list = new ArrayList<>(records.size());
        for (LearningLesson r : records) {
            LearningLessonVO vo = BeanUtils.copyBean(r, LearningLessonVO.class);
            //获取课程信息
            CourseSimpleInfoDTO cInfo = cMap.get(r.getCourseId());
            vo.setCourseName(cInfo.getName());
            vo.setCourseCoverUrl(cInfo.getCoverUrl());
            vo.setSections(cInfo.getSectionNum());
            list.add(vo);
        }
        return PageDTO.of(page, list);
    }

    private Map<Long, CourseSimpleInfoDTO> queryCourseSimpleInfoList(List<LearningLesson> records) {
        //查询课程信息
        Set<Long> cIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        List<CourseSimpleInfoDTO> classInfoList = courseClient.getSimpleInfoList(cIds);
        if(CollUtils.isEmpty(classInfoList)){
            throw new BadRequestException("课程信息不存在");
        }

        //把课程集合处理为一个Map key是courseId,值是course本身
        return classInfoList.stream()
                .collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
    }

    /**
     * 从用户课表中移除指定课程；未显式传入用户时删除当前登录用户的课表记录。
     *
     * @param userId 用户编号，可为空
     * @param courseId 要移除的课程编号
     */
    @Override
    public void deleteCourseFromLesson(Long userId, Long courseId) {
        // 1.获取当前登录用户
        if (userId == null) {
            userId = UserContext.getUser();
        }
        // 2.删除课程
        remove(buildUserIdAndCourseIdWrapper(userId, courseId));
    }

    /**
     * 查询当前登录用户指定课程的课表信息。
     *
     * @param courseId 课程编号
     * @return 课表信息；课程不在个人课表中时返回 {@code null}
     */
    @Override
    public LearningLessonVO queryLessonByCourseId(Long courseId) {
        // 1.获取当前登录用户
        Long userId = UserContext.getUser();
        // 2.查询课程信息 select * from xx where user_id = #{userId} AND course_id = #{courseId}
        LearningLesson lesson = getOne(buildUserIdAndCourseIdWrapper(userId, courseId));
        if (lesson == null) {
            return null;
        }
        // 3.处理VO
        return BeanUtils.copyBean(lesson, LearningLessonVO.class);
    }

    /**
     * 查询当前登录用户最近学习的一门进行中课程，并补充课程和最近小节信息。
     *
     * @return 当前学习课程；不存在进行中课程时返回 {@code null}
     */
    @Override
    public LearningLessonVO queryMyCurrentLesson() {
        // 1.获取当前登录的用户
        Long userId = UserContext.getUser();
        // 2.查询正在学习的课程 select * from xx where user_id = #{userId} AND status = 1 order by latest_learn_time limit 1
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getStatus, LessonStatus.LEARNING.getValue())
                .orderByDesc(LearningLesson::getLatestLearnTime)
                .last("limit 1")
                .one();
        if (lesson == null) {
            return null;
        }
        // 3.拷贝PO基础属性到VO
        LearningLessonVO vo = BeanUtils.copyBean(lesson, LearningLessonVO.class);
        // 4.查询课程信息
        CourseFullInfoDTO cInfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
        if (cInfo == null) {
            throw new BadRequestException("课程不存在");
        }
        vo.setCourseName(cInfo.getName());
        vo.setCourseCoverUrl(cInfo.getCoverUrl());
        vo.setSections(cInfo.getSectionNum());
        // 5.统计课表中的课程数量 select count(1) from xxx where user_id = #{userId}
        Integer courseAmount = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .count();
        vo.setCourseAmount(courseAmount);
        // 6.查询小节信息
        List<CataSimpleInfoDTO> cataInfos =
                catalogueClient.batchQueryCatalogue(CollUtils.singletonList(lesson.getLatestSectionId()));
        if (!CollUtils.isEmpty(cataInfos)) {
            CataSimpleInfoDTO cataInfo = cataInfos.get(0);
            vo.setLatestSectionName(cataInfo.getName());
            vo.setLatestSectionIndex(cataInfo.getCIndex());
        }
        return vo;
    }

    /**
     * 校验当前登录用户是否拥有指定课程且课表记录有效。
     *
     * @param courseId 课程编号
     * @return 对应课表编号；未登录或课程不在课表中时返回 {@code null}
     */
    @Override
    public Long isLessonValid(Long courseId) {
        // 1.获取登录用户
        Long userId = UserContext.getUser();
        if (userId == null) {
            return null;
        }
        // 2.查询课程
        LearningLesson lesson = getOne(buildUserIdAndCourseIdWrapper(userId, courseId));
        if (lesson == null) {
            return null;
        }
        return lesson.getId();
    }

    /**
     * 统计指定课程的有效学习人数。
     *
     * @param courseId 课程编号
     * @return 状态为未开始、学习中或已完成的课表记录数量
     */
    @Override
    public Integer countLearningLessonByCourse(Long courseId) {
        // select count(1) from xx where course_id = #{cc} AND status in (0, 1, 2)
        return lambdaQuery()
                .eq(LearningLesson::getCourseId, courseId)
                .in(LearningLesson::getStatus,
                        LessonStatus.NOT_BEGIN.getValue(),
                        LessonStatus.LEARNING.getValue(),
                        LessonStatus.FINISHED.getValue())
                .count();
    }

    /**
     * 查询指定用户在指定课程中的课表记录。
     *
     * @param userId 用户编号
     * @param courseId 课程编号
     * @return 课表记录；不存在时返回 {@code null}
     */
    @Override
    public LearningLesson queryByUserIdAndCourseId(Long userId, Long courseId) {
        return getOne(buildUserIdAndCourseIdWrapper(userId, courseId));
    }

    /**
     * 为当前登录用户的指定课程创建或更新每周学习频次计划。
     *
     * @param courseId 课程编号
     * @param freq 每周计划学习的小节数
     */
    @Override
    public void createLearningPlan(Long courseId, Integer freq) {
        // 1.获取当前登录的用户
        Long userId = UserContext.getUser();
        // 2.查询课表中的指定课程有关的数据
        LearningLesson lesson = queryByUserIdAndCourseId(userId, courseId);
        AssertUtils.isNotNull(lesson, "课程信息不存在！");
        // 3.修改数据
        LearningLesson l = new LearningLesson();
        l.setId(lesson.getId());
        l.setWeekFreq(freq);
        if(lesson.getPlanStatus() == PlanStatus.NO_PLAN) {
            l.setPlanStatus(PlanStatus.PLAN_RUNNING);
        }
        updateById(l);
    }

    /**
     * 分页查询当前登录用户正在执行的学习计划，并汇总本周计划和完成的小节数量。
     *
     * @param query 分页条件
     * @return 周学习统计及课程学习计划列表
     */
    @Override
    public LearningPlanPageVO queryMyPlans(PageQuery query) {
        LearningPlanPageVO result = new LearningPlanPageVO();
        // 1.获取当前登录用户
        Long userId = UserContext.getUser();
        // 2.获取本周起始时间
        LocalDate now = LocalDate.now();
        LocalDateTime begin = DateUtils.getWeekBeginTime(now);
        LocalDateTime end = DateUtils.getWeekEndTime(now);
        // 3.查询总的统计数据
        // 3.1.本周总的已学习小节数量
        Integer weekFinished = recordMapper.selectCount(new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId, userId)
                .eq(LearningRecord::getFinished, true)
                .gt(LearningRecord::getFinishTime, begin)
                .lt(LearningRecord::getFinishTime, end)
        );
        result.setWeekFinished(weekFinished);
        // 3.2.本周总的计划学习小节数量
        Integer weekTotalPlan = getBaseMapper().queryTotalPlan(userId);
        result.setWeekTotalPlan(weekTotalPlan);
        // TODO 3.3.本周学习积分

        // 4.查询分页数据
        // 4.1.分页查询课表信息以及学习计划信息
        Page<LearningLesson> p = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .in(LearningLesson::getStatus, LessonStatus.NOT_BEGIN, LessonStatus.LEARNING)
                .page(query.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = p.getRecords();
        if (CollUtils.isEmpty(records)) {
            return result.pageInfo(p.getTotal(), p.getPages(), null);
        }
        // 4.2.查询课表对应的课程信息
        Map<Long, CourseSimpleInfoDTO> cMap = queryCourseSimpleInfoList(records);
        // 4.3.统计每一个课程本周已学习小节数量
        List<IdAndNumDTO> list = recordMapper.countLearnedSections(userId, begin, end);
        Map<Long, Integer> countMap = IdAndNumDTO.toMap(list);
        // 4.4.组装数据VO
        List<LearningPlanVO> voList = new ArrayList<>(records.size());
        for (LearningLesson r : records) {
            // 4.4.1.拷贝基础属性到vo
            LearningPlanVO vo = BeanUtils.copyBean(r, LearningPlanVO.class);
            // 4.4.2.填充课程详细信息
            CourseSimpleInfoDTO cInfo = cMap.get(r.getCourseId());
            if (cInfo != null) {
                vo.setCourseName(cInfo.getName());
                vo.setSections(cInfo.getSectionNum());
            }
            // 4.4.3.每个课程的本周已学习小节数量
            vo.setWeekLearnedSections(countMap.getOrDefault(r.getId(), 0));
            voList.add(vo);
        }
        return result.pageInfo(p.getTotal(), p.getPages(), voList);

    }


    private LambdaQueryWrapper<LearningLesson> buildUserIdAndCourseIdWrapper(Long userId, Long courseId) {
        return new QueryWrapper<LearningLesson>()
                .lambda()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId);
    }
}
