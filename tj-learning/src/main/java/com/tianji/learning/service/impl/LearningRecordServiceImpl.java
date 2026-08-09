package com.tianji.learning.service.impl;

import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.SectionType;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.ILearningRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * <p>
 * 学习记录表 服务实现类
 * </p>
 *
 * @author author
 * @since 2026-08-06
 */
@Service
@RequiredArgsConstructor
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper, LearningRecord> implements ILearningRecordService {

    private final ILearningLessonService lessonService;
    private final CourseClient courseClient;

    @Override
    public LearningLessonDTO queryLearningRecordByCourse(Long courseId) {
        //获取登录用户
        Long userId = UserContext.getUser();
        //查询课表
        LearningLesson lesson = lessonService.queryByUserIdAndCourseId(userId, courseId);
        if (lesson == null) {
            return null;
        }
        //查询学习记录
        List<LearningRecord> records = lambdaQuery()
                .eq(LearningRecord::getLessonId, lesson.getId())
                .list();
        //封装记录
        LearningLessonDTO dto = new LearningLessonDTO();
        dto.setId(lesson.getId());
        dto.setLatestSectionId(lesson.getLatestSectionId());
        dto.setRecords(BeanUtils.copyList(records, LearningRecordDTO.class));
        return dto;
    }

    @Override
    @Transactional
    public void addLearningRecord(LearningRecordFormDTO recordDTO) {
        //获取登录用户
        Long userId = UserContext.getUser();
        //处理休息记录
        //这里判断的是，是否是一个新的完成小节
        boolean finished = false;
        if(recordDTO.getSectionType() == SectionType.VIDEO){
            //处理视频
            finished = handleVideoRecord(recordDTO, userId);
        }else{
            //处理考试
            finished = handleExamRecord(recordDTO, userId);
        }

        handleLearningLesson(recordDTO, finished);
    }

    /**
     * 处理学习课表
     * @param recordDTO
     * @param finished
     */
    private void handleLearningLesson(LearningRecordFormDTO recordDTO, boolean finished) {
        // 1.查询课表
        LearningLesson lesson = lessonService.getById(recordDTO.getLessonId());
        if (lesson == null) {
            throw new BizIllegalException("课程不存在，无法更新数据！");
        }
        // 2.判断是否有新的完成小节
        boolean allLearned = false;

        // 3.如果有新完成的小节，则需要查询课程数据
        CourseFullInfoDTO cInfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
        if (cInfo == null) {
            throw new BizIllegalException("课程不存在，无法更新数据！");
        }
        // 4.比较课程是否全部学完：已学习小节 >= 课程总小节
        allLearned = lesson.getLearnedSections() + 1 >= cInfo.getSectionNum();

        // 5.更新课表
        lessonService.lambdaUpdate()
                .set(lesson.getLearnedSections() == 0, LearningLesson::getStatus, LessonStatus.LEARNING.getValue())
                .set(allLearned, LearningLesson::getStatus, LessonStatus.FINISHED.getValue())
                .setSql("learned_sections = learned_sections + 1")
                .eq(LearningLesson::getId, lesson.getId())
                .update();
    }

    /**
     * 处理视频学习记录
     * @param recordDTO
     * @param userId
     * @return 是否完成学习
     */
    private boolean handleVideoRecord(LearningRecordFormDTO recordDTO, Long userId) {
        //查询旧的学习记录
        LearningRecord oldRecord = lambdaQuery()
                .eq(LearningRecord::getLessonId, recordDTO.getLessonId())
                .eq(LearningRecord::getSectionId, recordDTO.getSectionId())
                .one();
        //判断是否存在
        if (oldRecord == null) {
            //不存在则新增
            LearningRecord record = BeanUtils.copyBean(recordDTO, LearningRecord.class);
            record.setUserId(userId);
            boolean success = save(record);
            if(!success){
                throw new DbException("新增学习记录失败");
            }
            return false;
        }
        //存在则更新
        //旧状态是未完成，且本次播放进度超过总时长的一半，则认为完成学习
        boolean finished = !oldRecord.getFinished() && recordDTO.getMoment() * 2 >= recordDTO.getDuration();
        //更新记录
        boolean success = lambdaUpdate()
                .set(LearningRecord::getMoment, recordDTO.getMoment())
                .set(finished, LearningRecord::getFinished, true)
                .set(finished, LearningRecord::getFinishTime, recordDTO.getCommitTime())
                .eq(LearningRecord::getId, oldRecord.getId())
                .update();
        if (!success) {
            throw new DbException("更新学习记录失败");
        }
        return finished;
    }

    /**
     * 处理考试记录
     * @param recordDTO
     * @param userId
     * @return
     */
    private boolean handleExamRecord(LearningRecordFormDTO recordDTO, Long userId) {
        //转换DTO为PO
        LearningRecord record = BeanUtils.copyBean(recordDTO, LearningRecord.class);
        record.setUserId(userId);
        record.setFinished(true);
        record.setFinishTime(recordDTO.getCommitTime());
        boolean success = save(record);
        if(!success){
            throw new DbException("新增学习记录失败");
        }
        return false;
    }
}
