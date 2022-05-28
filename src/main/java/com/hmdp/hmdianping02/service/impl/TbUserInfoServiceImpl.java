package com.hmdp.hmdianping02.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.hmdianping02.dto.Result;
import com.hmdp.hmdianping02.entity.TbUserInfo;
import com.hmdp.hmdianping02.mapper.TbUserInfoMapper;
import com.hmdp.hmdianping02.service.ITbUserInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author sangeng
 * @since 2022-04-17
 */
@Service
public class TbUserInfoServiceImpl extends ServiceImpl<TbUserInfoMapper, TbUserInfo> implements ITbUserInfoService {
    @Autowired
    private TbUserInfoMapper tbUserInfoMapper;
    @Override
    public Result info(Long id) {
        TbUserInfo tbUserInfo = tbUserInfoMapper.selectById(id);
        return  Result.ok(tbUserInfo);
    }
}
