package com.hmdp.hmdianping02.mapper;

import com.hmdp.hmdianping02.entity.TbVoucher;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author sangeng
 * @since 2022-04-17
 */@Mapper
public interface TbVoucherMapper extends BaseMapper<TbVoucher> {

    List<TbVoucher> queryVoucherOfShop(Long shopId);
}
