package com.hmdp.hmdianping02.controller;


import cn.hutool.core.bean.BeanUtil;
import com.hmdp.hmdianping02.dto.LoginFormDTO;
import com.hmdp.hmdianping02.dto.Result;
import com.hmdp.hmdianping02.dto.UserDTO;
import com.hmdp.hmdianping02.entity.TbUser;
import com.hmdp.hmdianping02.service.impl.TbUserInfoServiceImpl;
import com.hmdp.hmdianping02.service.impl.TbUserServiceImpl;
import com.hmdp.hmdianping02.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;

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
public class TbUserController {
    @Autowired
    private TbUserServiceImpl tbUserService;
    @Autowired
    private TbUserInfoServiceImpl tbUserInfoService;
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session){

        //发送验证码并保存

        return tbUserService.sendCode(phone,session);
    }

    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginFormDTO, HttpSession session){

        //发送验证码并保存

        return tbUserService.login(loginFormDTO,session);
    }

    @GetMapping("/me")
    public Result me(){

        //返回登入用户信息
        Object user = UserHolder.getUser();
        return Result.ok(user);
    }
    @GetMapping("/{id}")
    public Result queryById(@PathVariable("id")Long userId){
        TbUser user = tbUserService.getById(userId);
        if (user==null){
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return Result.ok(userDTO);
    }
    @PostMapping("/sign")
    public Result sign(){
        return tbUserService.sign();
    }
    @GetMapping("/sign/count")
    public Result signCount(){
        return tbUserService.signCount();
    }
    @PostMapping("/hyper/count")
    public Result HyperLogLogCount(){
        return tbUserService.HyperLogLogCount();
    }
}
