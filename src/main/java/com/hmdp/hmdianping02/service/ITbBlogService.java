package com.hmdp.hmdianping02.service;

import com.hmdp.hmdianping02.dto.Result;
import com.hmdp.hmdianping02.entity.TbBlog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author sangeng
 * @since 2022-04-17
 */
public interface ITbBlogService extends IService<TbBlog> {

    Result queryHotBlog(Integer current);

    Result queryBlogById(Long id);

    Result likeBlog(Long id);

    Result queryBlogLikes(Long id);

    Result saveBlog(TbBlog blog);

    Result queryBlogOfFollow(Long max, Integer offset);


}
