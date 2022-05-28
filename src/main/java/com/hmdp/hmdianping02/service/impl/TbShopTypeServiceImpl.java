package com.hmdp.hmdianping02.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.hmdianping02.dto.Result;
import com.hmdp.hmdianping02.entity.TbShopType;
import com.hmdp.hmdianping02.mapper.TbShopTypeMapper;
import com.hmdp.hmdianping02.service.ITbShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.hmdianping02.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author sangeng
 * @since 2022-04-17
 */
@Service
public class TbShopTypeServiceImpl extends ServiceImpl<TbShopTypeMapper, TbShopType> implements ITbShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result quryTypeList() {
        //从redis查找
        String typekey=CACHE_SHOPTYP_KEY+1234;
        String shoptype = stringRedisTemplate.opsForValue().get(typekey);
        if (StrUtil.isNotBlank(shoptype)){
            List<TbShopType> tbShopTypes = JSONUtil.toList(shoptype, TbShopType.class);
            return Result.ok(tbShopTypes);
        }
        //没在redis中找到就去数据库中查找并且添加到redis中
        List<TbShopType> typeList = query()
                .orderByAsc("sort").list();

        stringRedisTemplate.opsForValue().set(typekey,JSONUtil.toJsonStr(typeList),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(typeList);
    }
}
