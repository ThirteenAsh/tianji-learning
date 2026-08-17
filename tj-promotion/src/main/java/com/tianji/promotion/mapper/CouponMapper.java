package com.tianji.promotion.mapper;

import com.tianji.promotion.domain.po.Coupon;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Update;

/**
 * <p>
 * 优惠券的规则信息 Mapper 接口
 * </p>
 *
 * @author ThirteenAsh
 * @since 2026-08-16
 */
public interface CouponMapper extends BaseMapper<Coupon> {

    @Update("update coupon set issue_num = issue_num + 1 where id = #{couponId}")
    void incrIssueNum(Long couponId);
}
