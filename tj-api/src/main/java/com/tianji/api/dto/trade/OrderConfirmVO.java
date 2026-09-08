package com.tianji.api.dto.trade;

import com.tianji.api.dto.promotion.CouponDiscountDTO;
import com.tianji.api.dto.promotion.OrderCourseDTO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "订单确认页信息")
public class OrderConfirmVO {
    @ApiModelProperty("订单id")
    private Long orderId;
    @ApiModelProperty("订单总金额")
    private Integer totalAmount;
    @ApiModelProperty("优惠折扣方案")
    private List<CouponDiscountDTO> discounts;
    @ApiModelProperty("订单中包含的课程")
    private List<OrderCourseDTO> courses;
}
