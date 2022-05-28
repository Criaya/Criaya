package com.hmdp.hmdianping02.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.hmdianping02.dto.Result;
import com.hmdp.hmdianping02.dto.UserDTO;
import com.hmdp.hmdianping02.entity.TbBlog;
import com.hmdp.hmdianping02.service.impl.TbBlogServiceImpl;
import com.hmdp.hmdianping02.service.impl.TbFollowServiceImpl;
import com.hmdp.hmdianping02.service.impl.TbUserServiceImpl;
import com.hmdp.hmdianping02.utils.SystemConstants;
import com.hmdp.hmdianping02.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author sangeng
 * @since 2022-04-17
 */
@RestController
@RequestMapping("/blog")
public class TbBlogController {
    @Resource
    private TbFollowServiceImpl followService;
    @Resource
    private TbBlogServiceImpl blogService;
    @Resource
    private TbUserServiceImpl tbUserService;
    @PostMapping
    public Result saveBlog(@RequestBody TbBlog tbBlog){

        return blogService.saveBlog(tbBlog);
    }

    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        return blogService.likeBlog(id);
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<TbBlog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<TbBlog> records = page.getRecords();
        return Result.ok(records);
    }

    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }
    @GetMapping("/{id}")
    public Result queryBolgById(@PathVariable("id")Long id){
        return blogService.queryBlogById(id);
    }
    @GetMapping("/likes/{id}")
    public Result queryBolgLikes(@PathVariable("id")Long id){
        return blogService.queryBlogLikes(id);
    }
    @GetMapping("/of/user")
    public Result queryBlogByUserId(@RequestParam(value = "current",defaultValue = "1")Integer current
                                    ,@RequestParam("id")Long id){
        //根据用户查询
        Page<TbBlog> tbBlogPage = blogService.query().eq("user_id", id)
                .page(new Page<TbBlog>(current, SystemConstants.MAX_PAGE_SIZE));
        //获取当前页面
        List<TbBlog> records = tbBlogPage.getRecords();
        return Result.ok(records);
    }
    @GetMapping("/of/follow")
    public Result queryBlogOfFollow(
            @RequestParam("lastId")Long max,@RequestParam(value = "offset",defaultValue = "0") Integer current){
        return blogService.queryBlogOfFollow(max,current);
    }
}
