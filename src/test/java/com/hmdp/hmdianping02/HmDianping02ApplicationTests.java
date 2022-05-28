package com.hmdp.hmdianping02;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.hmdianping02.entity.TbShop;
import com.hmdp.hmdianping02.mapper.TbBlogCommentsMapper;
import com.hmdp.hmdianping02.mapper.TbUserMapper;
import com.hmdp.hmdianping02.service.impl.TbShopServiceImpl;
import com.hmdp.hmdianping02.service.impl.TbVoucherServiceImpl;
import com.hmdp.hmdianping02.utils.MyRedisIdWork;
import com.hmdp.hmdianping02.utils.RedisData;
import com.hmdp.hmdianping02.utils.UserHolder;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@SpringBootTest
class HmDianping02ApplicationTests {

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private TbBlogCommentsMapper tbBlogCommentsMapper;

    @Autowired
    private TbUserMapper tbUserMapper;

    @Autowired
    private TbShopServiceImpl shopService;

    @Autowired
    private MyRedisIdWork redisIdWork;
    @Autowired
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @Autowired
    private TbVoucherServiceImpl tbVoucherService;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private TbShopServiceImpl tbShopService;

    @Test
    void contextLoads() {
        List<TbShop> list = tbShopService.list();
        Map<Long, List<TbShop>> shopMap = list.stream().collect(Collectors.groupingBy(TbShop::getTypeId));
        for (Map.Entry<Long, List<TbShop>> longListEntry : shopMap.entrySet()) {
            //获取店铺id
            Long keyId = longListEntry.getKey();
            String keys = "shop:geo:" + keyId;
            List<TbShop> value = longListEntry.getValue();
            //存进redis，类型作为key，店铺id作为value，经纬度
            //这里的String是member的类型
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            for (TbShop tbShop : value) {
                //一种方法但是效率不高
                //redisTemplate.opsForGeo().add(keys,new Point(tbShop.getX(),tbShop.getY()),tbShop.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>(tbShop.getId().toString()
                        , new Point(tbShop.getX(), tbShop.getY())));
            }
            redisTemplate.opsForGeo().add(keys, locations);
        }
    }

    @Test
    void Test() {
        // 准备数组，装用户数据
        String[] users = new String[1000];
        // 数组角标
        int index = 0;
        for (int i = 1; i <= 1000000; i++) {
            // 赋值
            users[index++] = "user_" + i;
            // 每1000条发送一次
            if (i % 1000 == 0) {
                index = 0;
                redisTemplate.opsForHyperLogLog().add("HyperLogLogCount:", users);
            }
        }
        // 统计数量
        //size = 997593
        Long size = redisTemplate.opsForHyperLogLog().size("HyperLogLogCount:");
        System.out.println("size = " + size);
    }

    @Test
    TbShop test() {
        Long id = 3L;
        String str = redisTemplate.opsForValue().get("shop:" + id);

        if (StrUtil.isBlank(str)) {
            return null;
        }
        RedisData redisData = BeanUtil.toBean(str, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        TbShop tbShop = JSONUtil.toBean(data, TbShop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            return tbShop;
        }
        if (true) {
            //异步更新
            CompletableFuture.runAsync(() -> sava(id, 3000L));
            //释放锁
        }
        return tbShop;
    }

    private void sava(Long id, Long time) {
        RedisData redisData = new RedisData();
        TbShop byId = shopService.getById(id);
        redisData.setData(byId);
        redisData.setData(LocalDateTime.now().plusSeconds(time));
        redisTemplate.opsForValue().set("shop:" + id, JSONUtil.toJsonStr(redisData));
    }

    private static final Long TIME = 1640995200L;

    @Test
    void time() {
        LocalDateTime now = LocalDateTime.now();
        String format = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long toEpochSecond = now.toEpochSecond(ZoneOffset.UTC);
        long code=toEpochSecond-TIME;
        //设置自增长redis
        Long increment = redisTemplate.opsForValue().increment("icr:" + format + ":", 1L);

         Long codes=code<<32| increment;
    }
    @Test
    void feed(){
        Long id = UserHolder.getUser().getId();
        String s="feed:"+id;
        Long time=0L;
        int conut=0;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = redisTemplate.opsForZSet().reverseRangeByScoreWithScores(s, 0, 1000, 0, 2);
        List<Long> list=new ArrayList<>(typedTuples.size());
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            Long value = Long.valueOf(typedTuple.getValue());
            list.add(value);
            //获取时间戳
            long score = Objects.requireNonNull(typedTuple.getScore()).longValue();
            if (score==time){
                conut++;
            }else {
                time=score;
            }

        }
    }
    @Test
    void redis(){
        String s = redisTemplate.opsForValue().get("blog:liked:27");
        System.out.println(s);
    }
}



