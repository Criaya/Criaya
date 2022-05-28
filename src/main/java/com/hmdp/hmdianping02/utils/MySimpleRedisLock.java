package com.hmdp.hmdianping02.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class MySimpleRedisLock implements ILock {
    private StringRedisTemplate stringRedisTemplate;
    //用户传来锁的名字，实现动态
    private String name;
    //前缀
    private static final String KEY_PREFIX = "lock:";
    //载入Lua脚本需要RedisScript，DefaultRedisScript是它的子类
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        //初始化
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        //使用applicationcontext的扩展资源的方法从类加载路径下读取unlock.lua
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        //设置类型
        UNLOCK_SCRIPT.setResultType(Long.class);

    }

    private String ID_PREFIX = UUID.randomUUID().toString(true);

    public MySimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        //用获取锁的线程作为值存进redis这个我们可以直观的看到是哪个线程获取锁
        //在集群环境下是多进程的所以各自的ThreadId不唯一唯一这里用uuid拼接线程id
        String threadId = Thread.currentThread().getId() + "-" + ID_PREFIX;
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId + "", timeoutSec, TimeUnit.SECONDS);
        //这样的写法可以避免空指针，是null也是返回false
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(KEY_PREFIX + name),
                Thread.currentThread().getId() + "-" + ID_PREFIX);
    }

//    @Override
//    public void unlock() {
//        String threadId = Thread.currentThread().getId()+ID_PREFIX;
//        //判断标识
//        String lockValue = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        if (lockValue.equals(threadId)){
//            stringRedisTemplate.delete(KEY_PREFIX+name);
//        }
//
//    }
}
