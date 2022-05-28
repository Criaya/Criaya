package com.hmdp.hmdianping02.service.impl;

import com.hmdp.hmdianping02.dto.Result;
import com.hmdp.hmdianping02.entity.TbSeckillVoucher;
import com.hmdp.hmdianping02.entity.TbVoucher;
import com.hmdp.hmdianping02.mapper.TbVoucherMapper;
import com.hmdp.hmdianping02.service.ITbVoucherService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author sangeng
 * @since 2022-04-17
 */
@Service
public class TbVoucherServiceImpl extends ServiceImpl<TbVoucherMapper, TbVoucher> implements ITbVoucherService {

    @Autowired
    private TbSeckillVoucherServiceImpl tbSeckillVoucherService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Override
    @Transactional
    public void addSeckillVoucher(TbVoucher tbVoucher) {
        //保存优惠券
        save(tbVoucher);
        //保存秒杀信息
        TbSeckillVoucher tbSeckillVoucher=new TbSeckillVoucher();
        tbSeckillVoucher.setVoucherId(tbVoucher.getId());
        tbSeckillVoucher.setStock(tbVoucher.getStock());
        tbSeckillVoucher.setBeginTime(tbVoucher.getBeginTime());
        tbSeckillVoucher.setEndTime(tbVoucher.getEndTime());
        tbSeckillVoucherService.save(tbSeckillVoucher);
        //把优惠卷信息保存到redis中，存库存
        redisTemplate.opsForValue().set("seckill:stock:" + tbVoucher.getId(), tbVoucher.getStock().toString());
    }

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 查询优惠券信息
        List<TbVoucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        // 返回结果
        return Result.ok(vouchers);
    }
}
