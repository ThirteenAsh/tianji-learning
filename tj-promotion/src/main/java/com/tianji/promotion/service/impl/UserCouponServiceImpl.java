package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.domain.query.UserCouponQuery;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.enums.ExchangeCodeStatus;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.strategy.discount.DiscountStrategy;
import com.tianji.promotion.utils.CodeUtil;
import com.tianji.promotion.utils.MyLock;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务实现类
 * </p>
 *
 * @author ThirteenAsh
 * @since 2026-08-17
 */
@Service
@RequiredArgsConstructor
public class UserCouponServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {

    private final CouponMapper couponMapper;
    private final IExchangeCodeService codeService;
    private final RedissonClient redissonClient;

    /**
     * 用户领取优惠券
     *
     * @param couponId 优惠券ID
     */
    @Override
    public void receiveCoupon(Long couponId) {
       // 1.查询优惠券
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            throw new BadRequestException("优惠券不存在");
        }
        // 2.校验发放时间
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getIssueBeginTime()) || now.isAfter(coupon.getIssueEndTime())) {
            throw new BadRequestException("优惠券发放已经结束或尚未开始");
        }
        // 3.校验库存
        if (coupon.getTotalNum() <= 0) {
            throw new BadRequestException("优惠券库存不足");
        }
        Long userId = UserContext.getUser();
        // 3.1.使用分布式锁，防止用户重复领取
        String key = "lock:coupon:uid:" + userId;
        IUserCouponService userCouponService = (IUserCouponService) AopContext.currentProxy();
        userCouponService.checkAndCreate(couponId, userId, coupon);

    }

    /**
     * 校验每人限领数量，并保存用户领取记录
     *
     * @param couponId 优惠券ID
     * @param userId   用户ID
     * @param coupon   优惠券对象
     */
    @MyLock(name = "lock:coupon")
    //Lock(name = "lock:coupon:#{userId}")
    //这个锁是基于SPEL表达式获取用户ID的，确保每个用户领取优惠券时是独立的锁
    @Transactional
    @Override
    public void checkAndCreate(Long couponId, Long userId, Coupon coupon) {
        LocalDateTime now = LocalDateTime.now();
        // 4.校验每人限领数量
        // 4.1.查询领取数量
        Integer count = lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getCouponId, couponId)
                .count();
        // 4.2.校验限领数量
        if (count != null && count >= coupon.getUserLimit()) {
            throw new BadRequestException("超出领取数量");
        }
        // 5.扣减优惠券库存 乐观锁解决超卖问题
        int r = couponMapper.incrIssueNum(couponId);
        if (r == 0) {
            throw new BizIllegalException("优惠券库存不足");
        }
        // 6.保存用户领取记录
        saveUserCoupon(couponId, userId, coupon, now);
    }

    /**
     * 用户使用兑换码兑换优惠券
     *
     * @param code 优惠券兑换码
     */
    @Override
    public void exchangeCoupon(String code) {
        // 1.校验并解析兑换码
        long serialNum = CodeUtil.parseCode(code);
        // 2.校验是否已经兑换 SETBIT KEY 4 1
        boolean exchanged = codeService.updateExchangeMark(serialNum, true);
        if (exchanged) {
            throw new BizIllegalException("兑换码已经被兑换过了");
        }
        try {
            // 3.查询兑换码对应的优惠券id
            ExchangeCode exchangeCode = codeService.getById(serialNum);
            if (exchangeCode == null) {
                throw new BizIllegalException("兑换码不存在！");
            }
            Coupon coupon = couponMapper.selectById(exchangeCode.getExchangeTargetId());
            // 4.是否过期
            LocalDateTime now = LocalDateTime.now();
            if (now.isAfter(coupon.getIssueEndTime()) || now.isBefore(coupon.getIssueBeginTime())) {
                throw new BizIllegalException("优惠券活动未开始或已经结束");
            }
            // 5.保存用户领取记录
            Long userId = UserContext.getUser();
            // 通过AopContext.currentProxy()获取当前代理对象，调用checkAndCreate方法，保证事务生效
            IUserCouponService userCouponService = (IUserCouponService) AopContext.currentProxy();
            userCouponService.checkAndCreate(coupon.getId(), userId, coupon);
            // 6.更新兑换码的兑换标记
            codeService.lambdaUpdate()
                    .set(ExchangeCode::getUserId, userId)
                    .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
                    .eq(ExchangeCode::getId, serialNum)
                    .update();
        } catch (Exception e) {
            codeService.updateExchangeMark(serialNum, false);
            throw e;
        }
    }

    /**
     * 分页查询我的优惠券
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Override
    public PageDTO<CouponVO> queryMyCouponPage(UserCouponQuery query) {
        // 1.获取当前用户
        Long userId = UserContext.getUser();
        // 2.分页查询用户券
        Page<UserCoupon> page = lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getStatus, query.getStatus())
                .page(query.toMpPage(new OrderItem("term_end_time", true)));
        List<UserCoupon> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }

        // 3.获取优惠券详细信息
        // 3.1.获取用户券关联的优惠券id
        Set<Long> couponIds = records.stream().map(UserCoupon::getCouponId).collect(Collectors.toSet());
        // 3.2.查询
        List<Coupon> coupons = couponMapper.selectBatchIds(couponIds);

        // 4.封装VO
        return PageDTO.of(page, BeanUtils.copyList(coupons, CouponVO.class));
    }

    /**
     * 核销指定优惠券
     *
     * @param userCouponIds 用户优惠券id集合
     */
    @Override
    @Transactional
    public void writeOffCoupon(List<Long> userCouponIds) {
        // 1.查询优惠券
        List<UserCoupon> userCoupons = listByIds(userCouponIds);
        if (CollUtils.isEmpty(userCoupons)) {
            return;
        }
        // 2.处理数据
        List<UserCoupon> list = userCoupons.stream()
                // 过滤无效券
                .filter(coupon -> {
                    if (coupon == null) {
                        return false;
                    }
                    if (UserCouponStatus.UNUSED != coupon.getStatus()) {
                        return false;
                    }
                    LocalDateTime now = LocalDateTime.now();
                    return !now.isBefore(coupon.getTermBeginTime()) && !now.isAfter(coupon.getTermEndTime());
                })
                // 组织新增数据
                .map(coupon -> {
                    UserCoupon c = new UserCoupon();
                    c.setId(coupon.getId());
                    c.setStatus(UserCouponStatus.USED);
                    return c;
                })
                .collect(Collectors.toList());

        // 4.核销，修改优惠券状态
        boolean success = updateBatchById(list);
        if (!success) {
            return;
        }
        // 5.更新已使用数量
        List<Long> couponIds = userCoupons.stream()
                .map(UserCoupon::getCouponId)
                .collect(Collectors.toList());
        int c = couponMapper.incrUsedNum(couponIds, 1);
        if (c < 1) {
            throw new DbException("更新优惠券使用数量失败！");
        }
    }

    /**
     * 退还指定优惠券
     *
     * @param userCouponIds 用户优惠券id集合
     */
    @Override
    @Transactional
    public void refundCoupon(List<Long> userCouponIds) {
        // 1.查询优惠券
        List<UserCoupon> userCoupons = listByIds(userCouponIds);
        if (CollUtils.isEmpty(userCoupons)) {
            return;
        }
        // 2.处理优惠券数据
        List<UserCoupon> list = userCoupons.stream()
                // 过滤无效券
                .filter(coupon -> coupon != null && UserCouponStatus.USED == coupon.getStatus())
                // 更新状态字段
                .map(coupon -> {
                    UserCoupon c = new UserCoupon();
                    c.setId(coupon.getId());
                    // 3.判断有效期，是否已经过期，如果过期，则状态为 已过期，否则状态为 未使用
                    LocalDateTime now = LocalDateTime.now();
                    UserCouponStatus status = now.isAfter(coupon.getTermEndTime()) ?
                            UserCouponStatus.EXPIRED : UserCouponStatus.UNUSED;
                    c.setStatus(status);
                    return c;
                }).collect(Collectors.toList());

        // 4.修改优惠券状态
        boolean success = updateBatchById(list);
        if (!success) {
            return;
        }
        // 5.更新已使用数量
        List<Long> couponIds = userCoupons.stream()
                .map(UserCoupon::getCouponId)
                .collect(Collectors.toList());
        int c = couponMapper.incrUsedNum(couponIds, -1);
        if (c < 1) {
            throw new DbException("更新优惠券使用数量失败！");
        }
    }

    @Override
    public List<String> queryDiscountRules(List<Long> userCouponIds) {
        // 1.查询优惠券信息
        List<Coupon> coupons = baseMapper.queryCouponByUserCouponIds(userCouponIds, UserCouponStatus.USED);
        if (CollUtils.isEmpty(coupons)) {
            return CollUtils.emptyList();
        }
        // 2.转换规则
        return coupons.stream()
                .map(c -> DiscountStrategy.getDiscount(c.getDiscountType()).getRule(c))
                .collect(Collectors.toList());
    }

    private void saveUserCoupon(Long couponId, Long userId, Coupon coupon, LocalDateTime now) {
        UserCoupon uc = new UserCoupon();
        uc.setCouponId(couponId);
        uc.setUserId(userId);
        LocalDateTime issueBeginTime = coupon.getIssueBeginTime();
        LocalDateTime issueEndTime = coupon.getIssueEndTime();
        if (issueBeginTime == null){
            issueBeginTime = now;
            issueEndTime = now.plusDays(coupon.getTermDays());
        }
        uc.setTermBeginTime(issueBeginTime);
        uc.setTermEndTime(issueEndTime);
        save(uc);
    }
}
