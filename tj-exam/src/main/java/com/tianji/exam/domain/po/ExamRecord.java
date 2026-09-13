package com.tianji.exam.domain.po;

import com.tianji.api.dto.exam.QuestionDTO;
import com.tianji.exam.domain.vo.ExamDetailVO;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Document("exam_record")
@CompoundIndex(name = "uk_exam_once", def = "{'userId': 1, 'sectionId': 1, 'type': 1}",
        unique = true, partialFilter = "{'type': 2}")
public class ExamRecord {

    @Id
    private String id;

    private Long userId;

    private Long lessonId;

    private Long courseId;

    private Long sectionId;

    private Integer type;

    private LocalDateTime startTime;

    private Boolean committed;

    private LocalDateTime commitTime;

    private Integer duration;

    private Integer score;

    private Boolean learningSynced;

    private Boolean statsSynced;

    private Boolean statsProcessing;

    private Boolean learningProcessing;

    private List<ExamDetailVO> details;

    /** 开考时保存题目快照；正确答案不能返回给学员端。 */
    private List<QuestionDTO> questions;
}
