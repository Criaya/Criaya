package com.hmdp.hmdianping02.service.impl;

import com.hmdp.hmdianping02.entity.TbSeckillVoucher;
import com.hmdp.hmdianping02.mapper.TbSeckillVoucherMapper;
import com.hmdp.hmdianping02.service.ITbSeckillVoucherService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 秒杀优惠券表，与优惠券是一对一关系 服务实现类
 * </p>
 *
 * @author sangeng
 * @since 2022-04-17
 */
@Service
public class TbSeckillVoucherServiceImpl extends ServiceImpl<TbSeckillVoucherMapper, TbSeckillVoucher> implements ITbSeckillVoucherService {

}
