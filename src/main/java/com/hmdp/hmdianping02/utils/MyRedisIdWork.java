package com.hmdp.hmdianping02.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class MyRedisIdWork {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    private static final long BEGIN_TIMESTAMP = 1640995200;
    /*
    序列号位数
     */
    private static final long COUNT_BITS = 32;

    public long nextId(String keyPrefix) {
        //生成时间戳
        LocalDateTime nowTime = LocalDateTime.now();
        long nowSecond = nowTime.toEpochSecond(ZoneOffset.UTC);
        long timeMap = nowSecond - BEGIN_TIMESTAMP;
        //生成序列号
        //因为一个key的自增是有上线的数值是有限的所以要使用多个key，然而这个key应该怎么办呢，应该使用天为单位的日期作为递增时间
        String dayTimeKey = nowTime.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //自增长
        long increment = stringRedisTemplate.opsForValue().increment("icr:"
                + keyPrefix + ":" + dayTimeKey);

        //拼接返回
        return timeMap << COUNT_BITS | increment;
    }
}
