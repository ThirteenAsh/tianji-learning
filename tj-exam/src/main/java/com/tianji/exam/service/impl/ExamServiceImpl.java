package com.tianji.exam.service.impl;

import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.learning.LearningClient;
import com.tianji.api.dto.course.CatalogueDTO;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.exam.QuestionDTO;
import com.tianji.api.dto.leanring.ExamLearningRecordDTO;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.exam.domain.dto.ExamStartDTO;
import com.tianji.exam.domain.dto.ExamAnswerDTO;
import com.tianji.exam.domain.dto.ExamSubmitDTO;
import com.tianji.exam.domain.po.ExamRecord;
import com.tianji.exam.domain.vo.ExamDetailVO;
import com.tianji.exam.domain.vo.ExamPageVO;
import com.tianji.exam.domain.vo.ExamQuestionDetailVO;
import com.tianji.exam.domain.vo.ExamQuestionVO;
import com.tianji.exam.domain.vo.ExamStartVO;
import com.tianji.exam.service.IExamService;
import com.tianji.exam.service.IQuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExamServiceImpl implements IExamService {

    private final IQuestionService questionService;
    private final LearningClient learningClient;
    private final CourseClient courseClient;
    private final CatalogueClient catalogueClient;
    private final MongoTemplate mongoTemplate;

    /**
     * 校验课程和小节资格，保存开始时间及题目快照，再向学员返回不含答案的题目。
     *
     * @param form 课程、小节和考试类型
     * @return 考试记录id及题目列表
     */
    @Override
    public ExamStartVO startExam(ExamStartDTO form) {
        // 1.获取当前登录用户，校验是否拥有该课程
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new BadRequestException("请先登录");
        }
        Long lessonId = learningClient.isLessonValid(form.getCourseId());
        if (lessonId == null) {
            throw new BadRequestException("尚未购买或无法学习该课程");
        }

        // 2.检查小节确实属于课程，且类型与练习/考试一致
        CourseFullInfoDTO course = courseClient.getCourseInfoById(form.getCourseId(), true, false);
        if (course == null || CollUtils.isEmpty(course.getChapters())) {
            throw new BadRequestException("课程或课程目录不存在");
        }
        CatalogueDTO chapter = null;
        CatalogueDTO section = null;
        for (CatalogueDTO candidate : course.getChapters()) {
            if (CollUtils.isEmpty(candidate.getSections())) {
                continue;
            }
            for (CatalogueDTO child : candidate.getSections()) {
                if (form.getSectionId().equals(child.getId())) {
                    chapter = candidate;
                    section = child;
                    break;
                }
            }
            if (section != null) {
                break;
            }
        }
        if (section == null) {
            throw new BadRequestException("小节不属于该课程");
        }
        boolean isExam = form.getType() == 2;
        if (isExam && !Integer.valueOf(3).equals(section.getType())
                || !isExam && !Integer.valueOf(2).equals(section.getType())) {
            throw new BadRequestException("小节类型与练习或考试不匹配");
        }

        // 3.章节考试必须位于章末，且该章所有视频小节已完成
        if (isExam) {
            List<CatalogueDTO> children = chapter.getSections();
            if (!form.getSectionId().equals(children.get(children.size() - 1).getId())) {
                throw new BadRequestException("章节考试不是该章最后一个小节");
            }
            LearningLessonDTO progress = learningClient.queryLearningRecordByCourse(form.getCourseId());
            if (progress == null || !lessonId.equals(progress.getId())) {
                throw new BadRequestException("无法确认课程学习进度");
            }
            Set<Long> finishedSections = new HashSet<>();
            if (CollUtils.isNotEmpty(progress.getRecords())) {
                finishedSections = progress.getRecords().stream()
                        .filter(record -> Boolean.TRUE.equals(record.getFinished()))
                        .map(LearningRecordDTO::getSectionId)
                        .collect(Collectors.toSet());
            }
            for (CatalogueDTO child : children) {
                if (Integer.valueOf(2).equals(child.getType())
                        && !finishedSections.contains(child.getId())) {
                    throw new BadRequestException("请先完成本章所有视频小节");
                }
            }

            // 4.章节考试每位学员只能参加一次
            Query oldExamQuery = Query.query(Criteria.where("userId").is(userId)
                    .and("sectionId").is(form.getSectionId())
                    .and("type").is(2));
            if (mongoTemplate.exists(oldExamQuery, ExamRecord.class)) {
                throw new BadRequestException("本章考试只能参加一次");
            }
        }

        // 5.查询并保存开考时的题目快照，用于后续交卷批阅
        List<QuestionDTO> questions = questionService.queryQuestionByBizId(form.getSectionId());
        if (CollUtils.isEmpty(questions)) {
            throw new BadRequestException("该小节没有关联题目");
        }
        ExamRecord record = new ExamRecord();
        record.setId(UUID.randomUUID().toString());
        record.setUserId(userId);
        record.setLessonId(lessonId);
        record.setCourseId(form.getCourseId());
        record.setSectionId(form.getSectionId());
        record.setType(form.getType());
        record.setStartTime(LocalDateTime.now());
        record.setCommitted(false);
        record.setLearningSynced(false);
        record.setStatsSynced(false);
        record.setStatsProcessing(false);
        record.setLearningProcessing(false);
        record.setQuestions(questions);
        try {
            mongoTemplate.insert(record);
        } catch (DuplicateKeyException e) {
            throw new BadRequestException("本章考试只能参加一次");
        }

        // 6.只返回题目展示字段，不向学员泄露正确答案和解析
        ExamStartVO result = new ExamStartVO();
        result.setId(record.getId());
        result.setQuestions(BeanUtils.copyList(questions, ExamQuestionVO.class));
        return result;
    }

    /**
     * 按开考时的题目快照批阅客观题，并以原子更新防止重复交卷。
     *
     * @param form 考试记录id和学员答案
     */
    @Override
    public void submitExam(ExamSubmitDTO form) {
        // 1.查询当前用户的考试记录
        Long userId = UserContext.getUser();
        ExamRecord record = queryOwnedRecord(form.getId(), userId);
        if (Boolean.TRUE.equals(record.getCommitted())) {
            syncExamSideEffects(record);
            return;
        }

        // 2.整理学员答案，拒绝重复题目或非本次试卷的题目
        Map<Long, QuestionDTO> questionMap = record.getQuestions().stream()
                .collect(Collectors.toMap(QuestionDTO::getId, question -> question));
        Map<Long, ExamAnswerDTO> answerMap = new HashMap<>();
        if (CollUtils.isNotEmpty(form.getExamDetails())) {
            for (ExamAnswerDTO answer : form.getExamDetails()) {
                if (!questionMap.containsKey(answer.getQuestionId())) {
                    throw new BadRequestException("提交了不属于本次考试的题目");
                }
                if (answerMap.putIfAbsent(answer.getQuestionId(), answer) != null) {
                    throw new BadRequestException("同一道题不能重复提交");
                }
                if (!questionMap.get(answer.getQuestionId()).getType()
                        .equals(String.valueOf(answer.getQuestionType()))) {
                    throw new BadRequestException("题目类型不匹配");
                }
            }
        }

        // 3.逐题批阅；未作答的题目记为错误、得0分
        List<ExamDetailVO> details = new ArrayList<>(record.getQuestions().size());
        int totalScore = 0;
        for (QuestionDTO question : record.getQuestions()) {
            ExamAnswerDTO submitted = answerMap.get(question.getId());
            String answer = submitted == null ? null : submitted.getAnswer();
            boolean correct = answer != null && !answer.isBlank()
                    && normalizeAnswer(answer, question.getType())
                    .equals(normalizeAnswer(question.getAnswer(), question.getType()));
            int score = correct && question.getScore() != null ? question.getScore() : 0;
            totalScore += score;

            ExamDetailVO detail = new ExamDetailVO();
            ExamQuestionDetailVO questionVO = BeanUtils.copyBean(question, ExamQuestionDetailVO.class);
            questionVO.setType(Integer.valueOf(question.getType()));
            detail.setQuestion(questionVO);
            detail.setAnswer(answer);
            detail.setCorrect(correct);
            detail.setScore(score);
            details.add(detail);
        }

        // 4.原子提交考试记录，只有首次交卷可以写入成绩
        LocalDateTime commitTime = LocalDateTime.now();
        long durationSeconds = Math.max(0, Duration.between(record.getStartTime(), commitTime).getSeconds());
        Query pending = Query.query(Criteria.where("_id").is(record.getId())
                .and("userId").is(userId).and("committed").is(false));
        Update update = new Update()
                .set("committed", true)
                .set("commitTime", commitTime)
                .set("duration", (int) Math.min(durationSeconds, Integer.MAX_VALUE))
                .set("score", totalScore)
                .set("details", details);
        ExamRecord committed = mongoTemplate.findAndModify(
                pending, update, FindAndModifyOptions.options().returnNew(true), ExamRecord.class);
        if (committed == null) {
            committed = queryOwnedRecord(form.getId(), userId);
        }

        // 5.更新题目统计，并在章节考试后通知学习服务
        syncExamSideEffects(committed);
    }

    /** 分页查询当前用户已交卷的练习和考试记录。 */
    @Override
    public PageDTO<ExamPageVO> queryMyExamPage(PageQuery pageQuery) {
        // 1.按当前用户和交卷状态统计总数
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new BadRequestException("请先登录");
        }
        Criteria criteria = Criteria.where("userId").is(userId).and("committed").is(true);
        long total = mongoTemplate.count(Query.query(criteria), ExamRecord.class);
        long pages = (total + pageQuery.getPageSize() - 1) / pageQuery.getPageSize();
        if (total == 0) {
            return PageDTO.empty(0L, 0L);
        }

        // 2.按交卷时间倒序分页，只读取列表所需字段
        Query query = Query.query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "commitTime"))
                .skip((long) (pageQuery.getPageNo() - 1) * pageQuery.getPageSize())
                .limit(pageQuery.getPageSize());
        query.fields().include("id").include("type").include("score")
                .include("commitTime").include("duration")
                .include("courseId").include("sectionId");
        List<ExamRecord> records = mongoTemplate.find(query, ExamRecord.class);
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(total, pages);
        }

        // 3.批量补充课程及小节名称
        Set<Long> courseIds = records.stream().map(ExamRecord::getCourseId).collect(Collectors.toSet());
        Set<Long> sectionIds = records.stream().map(ExamRecord::getSectionId).collect(Collectors.toSet());
        List<CourseSimpleInfoDTO> courses = courseClient.getSimpleInfoList(courseIds);
        List<CataSimpleInfoDTO> sections = catalogueClient.batchQueryCatalogue(sectionIds);
        Map<Long, String> courseNames = CollUtils.isEmpty(courses) ? Collections.emptyMap()
                : courses.stream().collect(Collectors.toMap(
                        CourseSimpleInfoDTO::getId, CourseSimpleInfoDTO::getName, (left, right) -> left));
        Map<Long, String> sectionNames = CollUtils.isEmpty(sections) ? Collections.emptyMap()
                : sections.stream().collect(Collectors.toMap(
                        CataSimpleInfoDTO::getId, CataSimpleInfoDTO::getName, (left, right) -> left));

        // 4.封装分页结果
        List<ExamPageVO> list = new ArrayList<>(records.size());
        for (ExamRecord record : records) {
            ExamPageVO vo = BeanUtils.copyBean(record, ExamPageVO.class);
            vo.setCourseName(courseNames.get(record.getCourseId()));
            vo.setSectionName(sectionNames.get(record.getSectionId()));
            list.add(vo);
        }
        return new PageDTO<>(total, pages, list);
    }

    /** 根据记录id查询当前用户的答题详情。 */
    @Override
    public List<ExamDetailVO> queryMyExamDetails(String id) {
        ExamRecord record = queryOwnedRecord(id, UserContext.getUser());
        if (!Boolean.TRUE.equals(record.getCommitted())) {
            throw new BadRequestException("试卷尚未提交");
        }
        return record.getDetails();
    }

    private ExamRecord queryOwnedRecord(String id, Long userId) {
        if (userId == null) {
            throw new BadRequestException("请先登录");
        }
        ExamRecord record = mongoTemplate.findById(id, ExamRecord.class);
        if (record == null || !userId.equals(record.getUserId())) {
            throw new BadRequestException("考试记录不存在");
        }
        return record;
    }

    private String normalizeAnswer(String answer, String questionType) {
        if (answer == null) {
            return "";
        }
        if (!"2".equals(questionType) && !"3".equals(questionType)) {
            return answer.trim();
        }
        List<String> choices = new ArrayList<>();
        for (String choice : answer.split(",")) {
            choices.add(choice.trim());
        }
        Collections.sort(choices);
        return String.join(",", choices);
    }

    private void syncExamSideEffects(ExamRecord record) {
        if (!Boolean.TRUE.equals(record.getStatsSynced())) {
            // 1.通过原子状态认领题目统计，避免并发重复交卷时重复计数
            Query claimStats = Query.query(Criteria.where("_id").is(record.getId())
                    .and("statsSynced").is(false).and("statsProcessing").is(false));
            ExamRecord claimed = mongoTemplate.findAndModify(claimStats,
                    Update.update("statsProcessing", true), ExamRecord.class);
            if (claimed != null) {
                try {
                    Map<Long, Boolean> correctness = new HashMap<>();
                    for (ExamDetailVO detail : record.getDetails()) {
                        if (detail.getAnswer() != null && !detail.getAnswer().isBlank()) {
                            correctness.put(detail.getQuestion().getId(), detail.getCorrect());
                        }
                    }
                    if (!correctness.isEmpty()) {
                        questionService.recordAnswerStatistics(correctness);
                    }
                    mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(record.getId())),
                            new Update().set("statsSynced", true).set("statsProcessing", false), ExamRecord.class);
                } catch (RuntimeException e) {
                    mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(record.getId())),
                            Update.update("statsProcessing", false), ExamRecord.class);
                    throw e;
                }
            }
        }

        if (Integer.valueOf(2).equals(record.getType()) && !Boolean.TRUE.equals(record.getLearningSynced())) {
            // 2.认领学习记录同步；学习服务自身也会检查考试小节是否已完成
            Query claimLearning = Query.query(Criteria.where("_id").is(record.getId())
                    .and("learningSynced").is(false).and("learningProcessing").is(false));
            ExamRecord claimed = mongoTemplate.findAndModify(claimLearning,
                    Update.update("learningProcessing", true), ExamRecord.class);
            if (claimed != null) {
                try {
                    ExamLearningRecordDTO learning = new ExamLearningRecordDTO();
                    learning.setLessonId(record.getLessonId());
                    learning.setSectionId(record.getSectionId());
                    learning.setCommitTime(record.getCommitTime());
                    learningClient.addExamLearningRecord(learning);
                    mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(record.getId())),
                            new Update().set("learningSynced", true).set("learningProcessing", false),
                            ExamRecord.class);
                } catch (RuntimeException e) {
                    mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(record.getId())),
                            Update.update("learningProcessing", false), ExamRecord.class);
                    throw e;
                }
            }
        }
    }
}
