package com.tianji.promotion.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.UserCoupon;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.promotion.domain.query.UserCouponQuery;
import com.tianji.promotion.domain.vo.CouponVO;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务类
 * </p>
 *
 * @author ThirteenAsh
 * @since 2026-08-17
 */
public interface IUserCouponService extends IService<UserCoupon> {

    void receiveCoupon(Long couponId);

    void checkAndCreate(Long couponId, Long userId, Coupon coupon);

    void exchangeCoupon(String code);

    PageDTO<CouponVO> queryMyCouponPage(UserCouponQuery query);

    void writeOffCoupon(List<Long> userCouponIds);

    void refundCoupon(List<Long> userCouponIds);

    List<String> queryDiscountRules(List<Long> userCouponIds);
}
