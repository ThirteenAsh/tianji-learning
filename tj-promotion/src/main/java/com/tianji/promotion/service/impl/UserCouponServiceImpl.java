package com.tianji.promotion.service.impl;

import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.enums.ExchangeCodeStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.utils.CodeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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
        // 3.1.加用户锁，一人一单 intern 返回的是字符串常量池中的值，保证同一个用户的锁是同一个值
        synchronized (userId.toString().intern()) {
            IUserCouponService userCouponService = (IUserCouponService) AopContext.currentProxy();
            userCouponService.checkAndCreate(couponId, userId, coupon);
        }
    }

    /**
     * 校验每人限领数量，并保存用户领取记录
     *
     * @param couponId 优惠券ID
     * @param userId   用户ID
     * @param coupon   优惠券对象
     */
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
