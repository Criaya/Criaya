package com.hmdp.hmdianping02.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.conditions.update.UpdateChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.hmdianping02.dto.Result;
import com.hmdp.hmdianping02.dto.ScrollResult;
import com.hmdp.hmdianping02.dto.UserDTO;
import com.hmdp.hmdianping02.entity.TbBlog;
import com.hmdp.hmdianping02.entity.TbFollow;
import com.hmdp.hmdianping02.entity.TbUser;
import com.hmdp.hmdianping02.mapper.TbBlogMapper;
import com.hmdp.hmdianping02.service.ITbBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.hmdianping02.service.ITbFollowService;
import com.hmdp.hmdianping02.utils.SystemConstants;
import com.hmdp.hmdianping02.utils.UserHolder;
import jodd.util.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author sangeng
 * @since 2022-04-17
 */
@Service
public class TbBlogServiceImpl extends ServiceImpl<TbBlogMapper, TbBlog> implements ITbBlogService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private TbUserServiceImpl tbUserService;
    @Autowired
    private ITbFollowService iTbFollowService;


    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        //最开始的blog是没有用户的头像和名字信息的
        Page<TbBlog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<TbBlog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        //查询bolg
        TbBlog blog = getById(id);
        if (blog == null) {
            return Result.fail("该博客不存在");
        }
        this.queryBlogUser(blog);
        this.isBlogLiked(blog);
        //查询bolg有关id
        return Result.ok(blog);
    }

    @Override
    public Result likeBlog(Long id) {
        // 1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.判断当前登录用户是否已经点赞
        String likeKey = "blog:liked:" + id;
        //判断set集合里面是否有有这个元素
        Double score = stringRedisTemplate.opsForZSet().score(likeKey, userId.toString());
        //点过赞的直接return回去
        if (score == null) {
            //未点赞，可以点赞数据库加一
            boolean islike = update().setSql("liked=liked+1").eq("id", id).update();
            if (islike) {
                //往SortedSet中用时间戳用作sorted标识,zadd key value score
                stringRedisTemplate.opsForZSet().add(likeKey, userId.toString(), System.currentTimeMillis());
            }
        } else {
            // 4.如果已点赞，取消点赞
            // 4.1.数据库点赞数 -1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            // 4.2.把用户从Redis的set集合移除
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(likeKey, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String likeKey = "blog:liked:" + id;
        //查询top前5的点赞用户的对象 zrange key 0 4,获取id
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(likeKey, 0, 4);
        //空结果集
        if (top5==null||top5.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //解析其中的用户id
        List<Long> userIds = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        //根据id获取用户信息
        String idStr = StrUtil.join(",", userIds);
        //根据用户id查询用户WHERE id IN ( 5，1 ) ORDER BY FIELD(id, 5,1)
        List<UserDTO> userDTOS = tbUserService.query().in("id",userIds).
                last("ORDER BY FIELD(id,"+idStr+")").list()
                .stream().
                map(users -> BeanUtil.copyProperties(users, UserDTO.class)).
                collect(Collectors.toList());
        //返回
        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(TbBlog tbBlog) {
        UserDTO user = UserHolder.getUser();
        Long id = user.getId();
        tbBlog.setUserId(id);
        boolean save = save(tbBlog);
        if (save){
            //查询笔记作者的所有粉丝
            List<TbFollow> followUserId = iTbFollowService.query().eq("follow_user_id", id).list();
            //推送
            for (TbFollow tbFollow : followUserId) {
                //用粉丝id作为key存进redis
                String key="feed:"+tbFollow.getUserId();
                //推送
                stringRedisTemplate.opsForZSet().add(key,tbBlog.getId().toString(),System.currentTimeMillis());
            }
        }
        return Result.ok(tbBlog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer current) {
        //获取用户信息
        Long userId = UserHolder.getUser().getId();
        //获取redis里面的推送信息的博客id
        //查询收件箱ZREVRANGEBYSCORE key Max Min LIMIT offset count
        String key="feed:"+userId;
        //TypedTuple<String>里面的数据是value和Score
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().
                reverseRangeByScoreWithScores(key, 0, max, current, 2);
        //非空判断
        if (typedTuples==null||typedTuples.isEmpty()){
            return Result.ok();
        }
        //解析set集合获得时间戳和blog的id,偏移量指的是查询的时候要偏离几个元素，最少需要偏离一个
        List<Long> idList=new ArrayList<>(typedTuples.size());
        long mintime=0;
        int offset=1;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            idList.add(Long.valueOf(typedTuple.getValue()));
            long time = Objects.requireNonNull(typedTuple.getScore()).longValue();
            if (time==mintime){
                offset++;
            }else {
                mintime=time;
                offset=1;
            }
        }
        //把id查寻出blog
        String idStr = StrUtil.join(",", idList);
        //根据用户id查询用户WHERE id IN ( 5，1 ) ORDER BY FIELD(id, 5,1)
        List<TbBlog> blogs = query().in("id", idList).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (TbBlog blog : blogs) {
            queryBlogUser(blog);
            isBlogLiked(blog);
        }
        //封装最后结果
        ScrollResult scrollResult=new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setMinTime(mintime);
        scrollResult.setOffset(offset);
        return Result.ok(scrollResult);
    }



    private void isBlogLiked(TbBlog blog) {
        // 1.获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 用户未登录，无需查询是否点赞
            return;
        }
        Long userId = user.getId();
        // 2.判断当前登录用户是否已经点赞
        String key = "blog:liked:" + blog.getId();

        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        blog.setIsLike(BooleanUtil.isTrue(score != null));
    }

    private void queryBlogUser(TbBlog blog) {
        Long userId = blog.getUserId();
        TbUser user = tbUserService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
