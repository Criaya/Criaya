package com.hmdp.hmdianping02.service;

import com.hmdp.hmdianping02.dto.Result;
import com.hmdp.hmdianping02.entity.TbVoucher;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author sangeng
 * @since 2022-04-17
 */
public interface ITbVoucherService extends IService<TbVoucher> {

    void addSeckillVoucher(TbVoucher tbVoucher);

    Result queryVoucherOfShop(Long shopId);
}
