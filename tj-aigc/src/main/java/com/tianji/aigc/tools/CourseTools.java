package com.tianji.aigc.tools;

import cn.hutool.core.map.MapUtil;
import com.tianji.aigc.config.ToolResultHolder;
import com.tianji.aigc.constants.Constant;
import com.tianji.aigc.tools.result.CourseInfo;
import com.tianji.api.client.course.CourseClient;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 课程相关工具类
 */
@Component
@RequiredArgsConstructor
public class CourseTools {

    private final CourseClient courseClient;

    @Tool(description = Constant.Tools.QUERY_COURSE_BY_ID)
    public CourseInfo queryCourseById(@ToolParam(description = Constant.ToolParams.COURSE_ID)Long courseId, ToolContext toolContext) {
        return Optional.ofNullable(courseId)
                .map(id -> courseClient.baseInfo(id, true))
                .map(CourseInfo::of)
                .map(courseInfo -> {
                    String requestId = MapUtil.get(toolContext.getContext(), Constant.REQUEST_ID, String.class);
                    String field = "course_" + courseId;
                    ToolResultHolder.put(requestId, field, courseInfo);
                    return courseInfo;
                })
                .orElse(null);
    }
}
