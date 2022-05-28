package com.hmdp.hmdianping02.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.hmdianping02.utils.RedisConstants.*;

@Slf4j
@Component
public class MyCacheClient {
    @Autowired
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    private final StringRedisTemplate stringRedisTemplate;

    public MyCacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate=stringRedisTemplate;
    }

    //将任意Java对象序列化为json并存储在string类型的key中，并且可以设置TTL过期时间
    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }
    //将任意Java对象序列化为json并存储在string类型的key中，并且可以设置逻辑过期时间，用于处理缓存击穿问题
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        //RedisData进行存储
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }
    //根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
    public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallBack
    ,Long time, TimeUnit unit) {
        //从redis查询店铺信息
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //存在则返回
        if (StrUtil.isNotBlank(json)) {
            //把查出来的json转化为对象
            R r = JSONUtil.toBean(json, type);
            return r;
        }
        //缓存穿透之在redis中查到命中且为空值数据就直接返回
        if (json != null) {
            return null;
        }
        //不存在去数据库查找
        R db = dbFallBack.apply(id);
        //不存在返回错误
        if (db == null) {
            //缓存穿透解决方式之把null值写进redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //存在写进redis
        this.set(key,db,time,unit);
        return db;
    }

    //根据指定的key查询缓存，并反序列化为指定类型，需要利用逻辑过期解决缓存击穿问题
    public <R,ID> R queryWithLogicalExpire(String keyPrefix,ID id,Class<R> type,Function<ID,R> db,Long time, TimeUnit unit) {
        //不考虑缓存穿透
        //从redis查询店铺信息
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //存在则返回

        //查到开始复杂的逻辑
        //1，把得到的json转换成对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        //因为我们设置的类型是Object所以这里其实他会返回的类型是jsonObject
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data,type);
        //获取过期时间
        LocalDateTime expireTime = redisData.getExpireTime();
        //2判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //2.1没过期直接返回
            return r;
        }
        //2.2已过期需要进行缓存重建
        //3，缓存重建
        //3.1获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Boolean tryLock = tryLock(lockKey);
        //3.2判断是否获得锁成功
        if (tryLock) {
            //3.3成功开启独立线程缓存重建
            CompletableFuture.runAsync(() -> {
                try {
                    //查数据库
                    R r1 = db.apply(id);
                    //写入redis
                    this.setWithLogicalExpire(key,r1,time,unit);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    //释放锁
                    unLock(lockKey);
                }
            }, threadPoolTaskExecutor);
        }

        //3.4返回过期店铺信息
        return r;
    }
    //获取锁的方法
    private Boolean tryLock(String key) {
        Boolean aBoolean = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(aBoolean);
    }

    //释放锁方法
    private Boolean unLock(String key) {
        Boolean delete = stringRedisTemplate.delete(key);
        return BooleanUtil.isTrue(delete);
    }
}
