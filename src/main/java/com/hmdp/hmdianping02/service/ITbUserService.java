package com.hmdp.hmdianping02.service;

import com.hmdp.hmdianping02.dto.LoginFormDTO;
import com.hmdp.hmdianping02.dto.Result;
import com.hmdp.hmdianping02.entity.TbUser;
import com.baomidou.mybatisplus.extension.service.IService;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author sangeng
 * @since 2022-04-17
 */
public interface ITbUserService extends IService<TbUser> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginFormDTO, HttpSession session);

    Result sign();

    Result signCount();

    Result HyperLogLogCount();

}
