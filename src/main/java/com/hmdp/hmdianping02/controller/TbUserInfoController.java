package com.hmdp.hmdianping02.controller;


import com.hmdp.hmdianping02.dto.Result;
import com.hmdp.hmdianping02.service.impl.TbUserInfoServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author sangeng
 * @since 2022-04-17
 */
@RestController
@RequestMapping("/user")
public class TbUserInfoController {
    @Autowired
    private TbUserInfoServiceImpl tbUserInfoService;
    @RequestMapping("/info/{id}")
    public Result info(@PathVariable("id") Long id){
        return tbUserInfoService.info(id);
    }
}
