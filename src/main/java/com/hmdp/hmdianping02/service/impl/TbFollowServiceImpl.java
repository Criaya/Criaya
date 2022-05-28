package com.hmdp.hmdianping02.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.hmdianping02.dto.Result;
import com.hmdp.hmdianping02.dto.ScrollResult;
import com.hmdp.hmdianping02.dto.UserDTO;
import com.hmdp.hmdianping02.entity.TbBlog;
import com.hmdp.hmdianping02.entity.TbFollow;
import com.hmdp.hmdianping02.entity.TbUser;
import com.hmdp.hmdianping02.mapper.TbFollowMapper;
import com.hmdp.hmdianping02.service.ITbBlogService;
import com.hmdp.hmdianping02.service.ITbFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.hmdianping02.service.ITbUserService;
import com.hmdp.hmdianping02.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author sangeng
 * @since 2022-04-17
 */
@Service
public class TbFollowServiceImpl extends ServiceImpl<TbFollowMapper, TbFollow> implements ITbFollowService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ITbUserService iTbUserService;

    @Override
    public Result follow(Long id, Boolean isFollow) {
        //获取用户id
        Long userId = UserHolder.getUser().getId();
        //redis Key
        String key="follows:"+userId;
        //判断是否关注
        if (isFollow){
            //true关注
            TbFollow tbFollow=new TbFollow();
            tbFollow.setUserId(userId);
            tbFollow.setFollowUserId(id);
            boolean save = save(tbFollow);
            if (save){
                stringRedisTemplate.opsForSet().add(key,tbFollow.getFollowUserId().toString());
            }
        }else {
            //false取关
            boolean remove = remove(new QueryWrapper<TbFollow>().eq("user_id", userId).eq("follow_user_id", id));
            if (remove){
                //redis 移除
                stringRedisTemplate.opsForSet().remove(key,id);
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        Long userid = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userid).eq("follow_user_id", followUserId).count();
        return Result.ok(count>0);
    }

    @Override
    public Result followCommen(Long id) {
        Long userHolder = UserHolder.getUser().getId();
        //拼接key
        String key="follows:"+userHolder;
        String key2="follows:"+id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        if (intersect==null||intersect.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //解析id
        List<Long> longList = intersect.stream().map(Long::valueOf).collect(Collectors.toList());

        List<TbUser> tbUsers = iTbUserService.listByIds(longList);
        List<UserDTO> userDTOS = tbUsers.stream().map(tbUser -> BeanUtil.copyProperties(tbUser, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(userDTOS);
    }


}
