package com.hmdp.hmdianping02.service;

import com.hmdp.hmdianping02.dto.Result;
import com.hmdp.hmdianping02.entity.TbFollow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author sangeng
 * @since 2022-04-17
 */
public interface ITbFollowService extends IService<TbFollow> {

    Result follow(Long id, Boolean isFollow);

    Result isFollow(Long followUserId);

    Result followCommen(Long id);


}
