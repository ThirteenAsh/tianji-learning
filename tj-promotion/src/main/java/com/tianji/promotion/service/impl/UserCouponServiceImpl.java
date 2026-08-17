package com.tianji.promotion.service.impl;

import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.IUserCouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
        // 4.校验每人限领数量
        // 4.1.查询领取数量
        Integer count = lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getCouponId, couponId)
                .count();
        // 4.2.校验限领数量
        if(count != null && count >= coupon.getUserLimit()){
            throw new BadRequestException("超出领取数量");
        }
        // 5.扣减优惠券库存
        couponMapper.incrIssueNum(couponId);
        // 6.保存用户领取记录
        saveUserCoupon(couponId, userId, coupon, now);
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
