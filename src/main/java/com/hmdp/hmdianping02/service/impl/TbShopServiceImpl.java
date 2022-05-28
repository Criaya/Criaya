package com.hmdp.hmdianping02.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.hmdianping02.dto.Result;
import com.hmdp.hmdianping02.entity.TbBlog;
import com.hmdp.hmdianping02.entity.TbShop;
import com.hmdp.hmdianping02.mapper.TbShopMapper;
import com.hmdp.hmdianping02.service.ITbShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.hmdianping02.utils.MyCacheClient;
import com.hmdp.hmdianping02.utils.RedisData;
import com.hmdp.hmdianping02.utils.SystemConstants;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.hmdianping02.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author sangeng
 * @since 2022-04-17
 */
@Service
public class TbShopServiceImpl extends ServiceImpl<TbShopMapper, TbShop> implements ITbShopService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    @Autowired
    private MyCacheClient myCacheClient;

    @Override
    public Result queryShopById(Long id) {
        //缓存穿透，之工具类写法
//        TbShop tbShop = myCacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, TbShop.class, aLong -> getById(id), CACHE_SHOP_TTL, TimeUnit.MINUTES);

//        TbShop tbShop = getById(id);
//        myCacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + id,tbShop,200L,TimeUnit.SECONDS);
        //缓存击穿，互斥锁解决
        TbShop tbShop = queryWithPassMutex(id);

        //缓存击穿之逻辑过期
//        TbShop tbShop = myCacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, TbShop.class, aLong -> getById(id),CACHE_SHOP_TTL,TimeUnit.MINUTES);
        if (tbShop == null) {
            return Result.fail("店铺不存在");
        }
        //返回
        return Result.ok(tbShop);
    }

    public TbShop queryWithPassMutexTest(Long id) {
        //缓存到redis
        //缓存穿透
        //缓存击穿解决高热点key失效问题
        String keyId = CACHE_SHOP_KEY + id;
        String shop = stringRedisTemplate.opsForValue().get(keyId);
        if (StrUtil.isNotBlank(shop)) {
            return JSONUtil.toBean(shop, TbShop.class);
        }
        //中标之后值为空返回null
        if (shop != null) {
            return null;
        }
        //之所以要在这里开始缓存重建是因为前2步已经确定了redis中没有这个key的缓存
        TbShop ashop = null;
        String lockKey = null;
        try {
            lockKey = LOCK_SHOP_KEY + id;
            Boolean aBoolean = tryLock(lockKey);
            if (!aBoolean) {
                Thread.sleep(100);
                return queryWithPassMutexTest(id);
            }
            //没有查询到就从数据库查询数据并存储到redis中
            ashop = getById(id);
            if (ashop == null) {
                stringRedisTemplate.opsForValue().set(keyId, "", 3, TimeUnit.MINUTES);
            }
            //存到redis里
            stringRedisTemplate.opsForValue().set(keyId, JSONUtil.toJsonStr(ashop), 3, TimeUnit.HOURS);

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            unLock(lockKey);
        }
        return ashop;
    }

    public TbShop queryWithPassMutex(Long id) {
        //从redis查询店铺信息
        String shopkey = CACHE_SHOP_KEY + id;
        String shop = stringRedisTemplate.opsForValue().get(shopkey);
        //存在则返回
        if (StrUtil.isNotBlank(shop)) {
            //把查出来的json转化为对象
            return JSONUtil.toBean(shop, TbShop.class);
        }
        //缓存穿透之在redis中查到命中且为空值数据就直接返回
        if (shop != null) {
            return null;
        }
        String lockKey = null;
        TbShop shops = null;
        try {
            //4,实现缓存重建
            //4.1获取互斥锁
            lockKey = LOCK_SHOP_KEY + id;
            Boolean tryLock = tryLock(lockKey);
            //4.2判断是否获得成功锁
            if (!tryLock) {
                //4.2.1失败则休眠重试
                Thread.sleep(100);
                //递归
                return queryWithPassMutex(id);
            }

            //4.2.2成功，根据id查询数据库
            //模拟重建缓存的延迟
            Thread.sleep(300);
            //不存在去数据库查找
            shops = getById(id);
            //不存在返回错误
            if (shops == null) {
                //缓存穿透解决方式之把null值写进redis
                stringRedisTemplate.opsForValue().set(shopkey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //存在写进redis
            stringRedisTemplate.opsForValue().set(shopkey, JSONUtil.toJsonStr(shops), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            //7.释放互斥锁
            unLock(lockKey);
        }

        //返回
        return shops;
    }

    public TbShop queryWithLogicalExpire(Long id) {
        //不考虑缓存穿透
        //从redis查询店铺信息
        String shopkey = CACHE_SHOP_KEY + id;
        String shop = stringRedisTemplate.opsForValue().get(shopkey);
        //存在则返回
        if (StrUtil.isBlank(shop)) {
            //没查到直接返回null
            return null;
        }
        //查到开始复杂的逻辑
        //1，把得到的json转换成对象
        RedisData redisData = JSONUtil.toBean(shop, RedisData.class);
        //因为我们设置的类型是Object所以这里其实他会返回的类型是jsonObject
        JSONObject data = (JSONObject) redisData.getData();
        TbShop tbShop = JSONUtil.toBean(data, TbShop.class);
        //获取过期时间
        LocalDateTime expireTime = redisData.getExpireTime();
        //2判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //2.1没过期直接返回
            System.out.println(tbShop);
            return tbShop;
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
                    this.saveShopRedis(id, 200L);
                    System.out.println("我异步更新成功");
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    //释放锁
                    unLock(lockKey);
                }
            }, threadPoolTaskExecutor);
        }

        //3.4返回过期店铺信息
        System.out.println(tbShop);
        return tbShop;
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

    //封装存储店铺逻辑过期时间
    public void saveShopRedis(Long id, Long expireTime) {
        //查找店铺信息
        TbShop tbShop = getById(id);
        //封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(tbShop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireTime));
        //存储到redis里面,因为没有设置TTL所以默认永久有效
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    @Override
    @Transactional//实现原子性
    public Result updateShop(TbShop tbShop) {
        Long id = tbShop.getId();

        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        //更新数据库
        updateById(tbShop);
        //删除缓存
        String shopkey = CACHE_SHOP_KEY + id;
        stringRedisTemplate.delete(shopkey);
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 1.判断是否需要根据坐标查询
        if (x==null||y==null){
            Page<TbShop> page = query().eq("type_id", typeId).
                    page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
        //计算分页
        int begin=(current-1)*SystemConstants.DEFAULT_PAGE_SIZE;
        int end=current*SystemConstants.DEFAULT_PAGE_SIZE;
        //查询redis按照距离排序，分页，在得到shopid提供给数据库做查询和distance
        String key="shop:geo:"+typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> geoResults = stringRedisTemplate.opsForGeo().search(key,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));
        //解析出id获得我们存进去的那个集合
        if (geoResults==null){
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = geoResults.getContent();
        //截取分页数量
        //begin--emd
        List<Long> shopIds=new ArrayList<>(content.size());
        Map<Long, Distance> SDmap = new HashMap<>(content.size());
        content.stream().skip(begin).forEach(geoLocationGeoResult -> {
            //获取id并且收集起来
            Long  shopId = Long.valueOf(geoLocationGeoResult.getContent().getName());
            shopIds.add(shopId);
            //获取距离,收集使用Map让shopId作为key距离作为value
            Distance distance = geoLocationGeoResult.getDistance();
            SDmap.put(shopId,distance);
        });
        if (shopIds.isEmpty()){
            return Result.ok("没有下一页了");
        }
        //把id查寻出blog
        String idStr = StrUtil.join(",", shopIds);
        //根据用户id查询用户WHERE id IN ( 5，1 ) ORDER BY FIELD(id, 5,1)
        //因为要把距离和店铺关联起来所以我们要在数据库里面加进去距离的字段
        System.out.println(shopIds);
        List<TbShop> shops = query().in("id", shopIds).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (TbShop shop : shops) {
            shop.setDistance(SDmap.get(shop.getId()).getValue());
        }
        return Result.ok(shops);

//        if (x == null || y == null) {

//            // 不需要坐标查询，按数据库查询
//            Page<TbShop> page = query()
//                    .eq("type_id", typeId)
//                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
//            // 返回数据
//            return Result.ok(page.getRecords());
//        }

        // 2.计算分页参数
//        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
//        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
//
//        // 3.查询redis、按照距离排序、分页。结果：shopId、distance
//        String key = SHOP_GEO_KEY + typeId;
//        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo() // GEOSEARCH key BYLONLAT x y BYRADIUS 10 WITHDISTANCE
//                .search(
//                        key,
//                        GeoReference.fromCoordinate(x, y),
//                        new Distance(5000),
//                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
//                );
//        // 4.解析出id
//        if (results == null) {
//            return Result.ok(Collections.emptyList());
//        }
//        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
//        if (list.size() <= from) {
//            // 没有下一页了，结束
//            return Result.ok(Collections.emptyList());
//        }
//        // 4.1.截取 from ~ end的部分
//        List<Long> ids = new ArrayList<>(list.size());
//        Map<String, Distance> distanceMap = new HashMap<>(list.size());
//        list.stream().skip(from).forEach(result -> {
//            // 4.2.获取店铺id
//            String shopIdStr = result.getContent().getName();
//            ids.add(Long.valueOf(shopIdStr));
//            // 4.3.获取距离
//            Distance distance = result.getDistance();
//            distanceMap.put(shopIdStr, distance);
//        });
//        // 5.根据id查询Shop
//        String idStr = StrUtil.join(",", ids);
//        List<TbShop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
//        for (TbShop shop : shops) {
//            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
//        }
//        // 6.返回
//        return Result.ok(shops);
    }
}

