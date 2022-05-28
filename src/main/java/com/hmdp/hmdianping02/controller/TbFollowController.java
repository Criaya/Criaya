package com.hmdp.hmdianping02.controller;


import com.hmdp.hmdianping02.dto.Result;
import com.hmdp.hmdianping02.service.impl.TbFollowServiceImpl;
import com.hmdp.hmdianping02.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author sangeng
 * @since 2022-04-17
 */
@RestController
@RequestMapping("/follow")
public class TbFollowController {
    @Resource
    private TbFollowServiceImpl tbFollowService;
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long id,@PathVariable("isFollow") Boolean isFollow){
        return tbFollowService.follow(id,isFollow);
    }
    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long followUserId){
        return tbFollowService.isFollow(followUserId);
    }
    @GetMapping("/common/{id}")
    public Result followCommon(@PathVariable("id")Long id){
        return tbFollowService.followCommen(id);
    }
}
