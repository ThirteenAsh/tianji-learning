package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.mapper.PointsBoardSeasonMapper;
import com.tianji.learning.service.IPointsBoardSeasonService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 积分排行榜赛季服务实现。
 * <p>
 * 根据指定时间匹配其所属的积分榜赛季，为榜单归档、历史赛季查询等业务提供赛季标识。
 * </p>
 *
 * @author ThirteenAsh
 * @since 2026-08-14
 */
@Service
public class PointsBoardSeasonServiceImpl extends ServiceImpl<PointsBoardSeasonMapper, PointsBoardSeason> implements IPointsBoardSeasonService {

    /**
     * 查询指定时间所属的积分排行榜赛季。
     *
     * @param time 待匹配的业务时间
     * @return 赛季编号；没有匹配赛季时返回 {@code null}
     */
    @Override
    public Integer querySeasonByTime(LocalDateTime time) {
        Optional<PointsBoardSeason> optional = lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, time)
                .ge(PointsBoardSeason::getEndTime, time)
                .oneOpt();
        return optional.map(PointsBoardSeason::getId).orElse(null);
    }
}
