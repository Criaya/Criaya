package com.hmdp.hmdianping02.service;

import com.hmdp.hmdianping02.dto.Result;
import com.hmdp.hmdianping02.entity.TbShop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author sangeng
 * @since 2022-04-17
 */
public interface ITbShopService extends IService<TbShop> {

    Result queryShopById(Long id);


    Result updateShop(TbShop tbShop);

    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
