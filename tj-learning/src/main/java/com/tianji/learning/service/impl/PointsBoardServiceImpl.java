package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardItemVO;
import com.tianji.learning.domain.vo.PointsBoardVO;
import com.tianji.learning.mapper.PointsBoardMapper;
import com.tianji.learning.service.IPointsBoardService;
import com.tianji.learning.utils.TableInfoContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.BoundZSetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.tianji.learning.constants.RedisConstants.POINTS_BOARD_TABLE_PREFIX;

/**
 * 学习积分天梯榜服务实现。
 * <p>
 * 当期榜单从 Redis 有序集合读取，以支持实时积分和排名查询；历史赛季榜单预留从赛季分表查询的能力。
 * 返回榜单时会补充用户昵称，并同时提供当前用户的积分与排名。
 * </p>
 *
 * @author ThirteenAsh
 * @since 2026-08-14
 */
@Service
@RequiredArgsConstructor
public class PointsBoardServiceImpl extends ServiceImpl<PointsBoardMapper, PointsBoard> implements IPointsBoardService {

    private final StringRedisTemplate redisTemplate;

    private final UserClient userClient;

    /**
     * 分页查询指定赛季的积分榜，并返回当前登录用户在该榜单中的积分和排名。
     * <p>未指定赛季或赛季值为 {@code 0} 时查询当期实时榜单；否则查询历史赛季榜单。</p>
     *
     * @param query 榜单赛季及分页条件
     * @return 包含个人排名和榜单条目的结果
     */
    @Override
    public PointsBoardVO queryPointsBoardBySeason(PointsBoardQuery query) {
        // 1.判断是否是查询当前赛季
        Long season = query.getSeason();
        boolean isCurrent = season == null || season == 0;
        // 2.获取Redis的Key
        LocalDateTime now = LocalDateTime.now();
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + now.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
        // 2.查询我的积分和排名
        PointsBoard myBoard = isCurrent ?
                queryMyCurrentBoard(key) : // 查询当前榜单（Redis）
                queryMyHistoryBoard(season); // 查询历史榜单（MySQL）
        // 3.查询榜单列表
        List<PointsBoard> list = isCurrent ?
                queryCurrentBoardList(key, query.getPageNo(), query.getPageSize()) :
                queryHistoryBoardList(query);
        // 4.封装VO
        PointsBoardVO vo = new PointsBoardVO();
        // 4.1.处理我的信息
        if (myBoard != null) {
            vo.setPoints(myBoard.getPoints());
            vo.setRank(myBoard.getRank());
        }
        if (CollUtils.isEmpty(list)) {
            return vo;
        }
        // 4.2.查询用户信息
        Set<Long> uIds = list.stream().map(PointsBoard::getUserId).collect(Collectors.toSet());
        List<UserDTO> users = userClient.queryUserByIds(uIds);
        Map<Long, String> userMap = new HashMap<>(uIds.size());
        if(CollUtils.isNotEmpty(users)) {
            userMap = users.stream().collect(Collectors.toMap(UserDTO::getId, UserDTO::getName));
        }
        // 4.3.转换VO
        List<PointsBoardItemVO> items = new ArrayList<>(list.size());
        for (PointsBoard p : list) {
            PointsBoardItemVO v = new PointsBoardItemVO();
            v.setPoints(p.getPoints());
            v.setRank(p.getRank());
            v.setName(userMap.get(p.getUserId()));
            items.add(v);
        }
        vo.setBoardList(items);
        return vo;
    }

    /**
     * 为指定赛季创建独立的历史积分榜分表。
     *
     * @param season 赛季编号，必须为正整数
     * @throws IllegalArgumentException 赛季编号不合法时抛出
     */
    @Override
    public void createPointsBoardTableBySeason(Integer season) {
        if (season == null || season <= 0) {
            throw new IllegalArgumentException("赛季ID不合法");
        }
        getBaseMapper().createPointsBoardTable("points_board_" + season);
    }
    /**
     * 查询历史赛季的积分榜列表。
     *
     * @param query 包含赛季和分页条件的查询对象
     * @return 历史赛季的积分榜条目列表
     */
    private List<PointsBoard> queryHistoryBoardList(PointsBoardQuery query) {
        // 1.计算表名
        TableInfoContext.setInfo(POINTS_BOARD_TABLE_PREFIX + query.getSeason());
        // 2.查询数据
        Page<PointsBoard> page = page(query.toMpPage());
        // 3.数据处理
        List<PointsBoard> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return CollUtils.emptyList();
        }
        records.forEach(b -> b.setRank(b.getId().intValue()));
        return records;
    }

    /**
     * 从 Redis 当期排行榜中按积分倒序读取一页用户排名。
     *
     * @param key 当期排行榜 Redis 键
     * @param pageNo 页码，从 {@code 1} 开始
     * @param pageSize 每页条数
     * @return 包含用户、积分和排名的榜单条目
     */
    @Override
    public List<PointsBoard> queryCurrentBoardList(String key, Integer pageNo, Integer pageSize) {
        // 1.计算分页
        int from = (pageNo - 1) * pageSize;
        // 2.查询 对应的命令为 ZREVRANGE key from to WITHSCORES
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                .reverseRangeWithScores(key, from, from + pageSize - 1);
        if (CollUtils.isEmpty(tuples)) {
            return CollUtils.emptyList();
        }
        // 3.封装
        int rank = from + 1; //计数器
        List<PointsBoard> list = new ArrayList<>(tuples.size());
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String userId = tuple.getValue();
            Double points = tuple.getScore();
            if (userId == null || points == null) {
                continue;
            }
            PointsBoard p = new PointsBoard();
            p.setUserId(Long.valueOf(userId));
            p.setPoints(points.intValue());
            p.setRank(rank++);
            list.add(p);
        }
        return list;
    }

    /**
     * 查询历史赛季中当前登录用户的积分和排名。
     *
     * @param season 赛季编号
     * @return 当前用户在该赛季的积分榜条目，若未上榜则返回 {@code null}
     */
    private PointsBoard queryMyHistoryBoard(Long season) {
        // 1.获取登录用户
        Long userId = UserContext.getUser();
        // 2.计算表名
        TableInfoContext.setInfo(POINTS_BOARD_TABLE_PREFIX + season);
        // 3.查询数据
        Optional<PointsBoard> opt = lambdaQuery().eq(PointsBoard::getUserId, userId).oneOpt();
        if (opt.isEmpty()) {
            return null;
        }
        // 4.转换数据
        PointsBoard pointsBoard = opt.get();
        pointsBoard.setRank(pointsBoard.getId().intValue());
        return pointsBoard;
    }

    /**
     * 查询当前赛季中当前登录用户的积分和排名。
     *
     * @param key 当期排行榜 Redis 键
     * @return 当前用户在该赛季的积分榜条目，若未上榜则返回积分为 {@code 0} 且排名为 {@code 0}
     */
    private PointsBoard queryMyCurrentBoard(String key) {
        // 1.绑定key
        BoundZSetOperations<String, String> ops = redisTemplate.boundZSetOps(key);
        // 2.获取当前用户信息
        String userId = UserContext.getUser().toString();
        // 3.查询积分
        Double points = ops.score(userId);
        // 4.查询排名
        Long rank = ops.reverseRank(userId);
        // 5.封装返回
        PointsBoard p = new PointsBoard();
        p.setPoints(points == null ? 0 : points.intValue());
        p.setRank(rank == null ? 0 : rank.intValue() + 1);
        return p;
    }
}
