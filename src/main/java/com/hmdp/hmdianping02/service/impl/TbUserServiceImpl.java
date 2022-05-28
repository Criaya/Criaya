package com.hmdp.hmdianping02.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.hmdp.hmdianping02.dto.LoginFormDTO;
import com.hmdp.hmdianping02.dto.Result;
import com.hmdp.hmdianping02.dto.UserDTO;
import com.hmdp.hmdianping02.entity.TbUser;
import com.hmdp.hmdianping02.mapper.TbUserMapper;
import com.hmdp.hmdianping02.service.ITbUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.hmdianping02.utils.RegexUtils;
import com.hmdp.hmdianping02.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.hmdp.hmdianping02.utils.RedisConstants.*;
import static com.hmdp.hmdianping02.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author sangeng
 * @since 2022-04-17
 */
@Slf4j
@Service
public class TbUserServiceImpl extends ServiceImpl<TbUserMapper, TbUser> implements ITbUserService {

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1，验证手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2，如果不符合，返回错误信息
            return Result.fail("手机号码错误，请重新输入");
        }

        //符合生成验证码,使用hutool的工具类
        String code = RandomUtil.randomNumbers(6);
        //保存验证码到redis
        redisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //发送验证码,后面有条件更换为真的
        log.debug("发送短信验证码成功，验证码：", code);
        System.out.println(code);
        //返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginFormDTO, HttpSession session) {
        //1，验证手机号
        String phone = loginFormDTO.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2，如果不符合，返回错误信息
            return Result.fail("手机号码错误，请重新输入");
        }

        //从redis获取验证码并且校验验证码
        String code = redisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        System.out.println(code);
        String loginFormDTOCode = loginFormDTO.getCode();
        if (!loginFormDTOCode.equals(code)) {
            //不一致报错
            return Result.fail("手机号码或者验证码错误");
        }
        //一致根据手机查询用户
        TbUser user = query().eq("phone", phone).one();
        //判断是否存在
        if (user == null) {
            //不存在，创建用户并保存
            user=createuserwithphone(phone);
        }

        //保存用户信息到redis中用uuid作为唯一标识
        String token = UUID.randomUUID().toString();
        String tokenkey=LOGIN_USER_KEY+token;
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        //用BeanUtil把userdto转化成map
        Map<String, Object> usermap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor(
                        (filename,filevalue)->filevalue.toString()));
        redisTemplate.opsForHash().putAll(tokenkey,usermap);
        //设置有效期30min
        redisTemplate.expire(tokenkey,30,TimeUnit.MINUTES);
        //把token返回
        return Result.ok(token);
    }

    @Override
    public Result sign() {
        //获取当前用户id
        Long userId = UserHolder.getUser().getId();
        //获取当前日期
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        //拼接key
        String key="sign:"+userId+keySuffix;
        //获取当前日期，本月第几天
        int dayOfMonth = now.getDayOfMonth();
        //因为BitMap是0到30所以这边要日期减一
        redisTemplate.opsForValue().setBit(key,dayOfMonth-1,true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        //获取userId
        Long userId = UserHolder.getUser().getId();
        //获取当前日期
        LocalDateTime now = LocalDateTime.now();
        //转换日期格式
        String ketSuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        //拼接key
        String key="sign:"+userId+ketSuffix;
        //获取当前月有几天
        int dayOfMonth = now.getDayOfMonth();
        //获取当月签到十进制
        //5.获取本月截止今天为止的所有的签到记录，返回的是一个十进制的数字BITFIELD sign:5:202203 GET u14 0
        List<Long> result = redisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create().
                        get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if (result==null||result.isEmpty()){
            return Result.ok(0);
        }
        Long aLong = result.get(0);
        int count=0;
        if (aLong==null||aLong==0){
            return Result.ok(0);
        }
        //开始从后面循环遍历连续签到的天数
        while (true){
            if ((aLong&1)==0){
                break;
            }else {
                count++;
                //把along右移动一位在把他赋值给along
                aLong >>>=1;
            }
        }
        return Result.ok(count);
    }

    @Override
    public Result HyperLogLogCount() {
        Long userId = UserHolder.getUser().getId();
        String key="HyperLogLogCount:";
        redisTemplate.opsForHyperLogLog().add(key,userId.toString());
        return Result.ok();
    }

    private TbUser createuserwithphone(String phone) {
        TbUser tbUser=new TbUser();
        tbUser.setPhone(phone);
        tbUser.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        save(tbUser);
        return tbUser;
    }
}
