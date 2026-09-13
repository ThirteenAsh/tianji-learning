package com.tianji.exam.mapper;

import com.tianji.api.dto.IdAndNumDTO;
import com.tianji.exam.domain.po.Question;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * <p>
 * 题目 Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2022-09-02
 */
public interface QuestionMapper extends BaseMapper<Question> {

    List<IdAndNumDTO> countQuestionOfCreater(@Param("createrIds") List<Long> createrIds);

    @Update("UPDATE question SET answer_times = COALESCE(answer_times, 0) + 1, "
            + "correct_times = COALESCE(correct_times, 0) + #{correct} WHERE id = #{id}")
    int incrementAnswerTimes(@Param("id") Long id, @Param("correct") int correct);

}
