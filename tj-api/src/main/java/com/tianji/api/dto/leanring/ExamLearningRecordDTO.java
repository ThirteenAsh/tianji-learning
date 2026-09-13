package com.tianji.api.dto.leanring;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExamLearningRecordDTO {

    /** 2-考试小节。 */
    private Integer sectionType = 2;

    private Long lessonId;

    private Long sectionId;

    private LocalDateTime commitTime;
}
