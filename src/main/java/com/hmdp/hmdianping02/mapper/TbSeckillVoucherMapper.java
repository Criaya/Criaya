package com.hmdp.hmdianping02.mapper;

import com.hmdp.hmdianping02.entity.TbSeckillVoucher;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 秒杀优惠券表，与优惠券是一对一关系 Mapper 接口
 * </p>
 *
 * @author sangeng
 * @since 2022-04-17
 */@Mapper
public interface TbSeckillVoucherMapper extends BaseMapper<TbSeckillVoucher> {

}
