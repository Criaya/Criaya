package com.hmdp.hmdianping02.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.hmdianping02.dto.Result;
import com.hmdp.hmdianping02.dto.UserDTO;
import com.hmdp.hmdianping02.entity.TbSeckillVoucher;
import com.hmdp.hmdianping02.entity.TbVoucher;
import com.hmdp.hmdianping02.entity.TbVoucherOrder;
import com.hmdp.hmdianping02.mapper.TbVoucherOrderMapper;
import com.hmdp.hmdianping02.service.ITbVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.hmdianping02.utils.MyRedisIdWork;
import com.hmdp.hmdianping02.utils.MySimpleRedisLock;
import com.hmdp.hmdianping02.utils.SimpleRedisLock;
import com.hmdp.hmdianping02.utils.UserHolder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author sangeng
 * @since 2022-04-17
 */
@Service
@Slf4j
public class TbVoucherOrderServiceImpl extends ServiceImpl<TbVoucherOrderMapper, TbVoucherOrder> implements ITbVoucherOrderService {

    @Autowired
    private TbSeckillVoucherServiceImpl tbSeckillVoucherService;
    @Autowired
    private RedissonClient redissonClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private MyRedisIdWork myRedisIdWork;
    @Autowired
    private ThreadPoolTaskExecutor poolTaskExecutor;

    //载入Lua脚本需要RedisScript，DefaultRedisScript是它的子类
    private static final DefaultRedisScript<Long> seckill_SCRIPT;

    //创建阻塞队列
//    private BlockingQueue<TbVoucherOrder> blockingQueue=new ArrayBlockingQueue<>(1024*1024);
    static {
        //初始化
        seckill_SCRIPT = new DefaultRedisScript<>();
        //使用applicationcontext的扩展资源的方法从类加载路径下读取unlock.lua
        seckill_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        //设置类型
        seckill_SCRIPT.setResultType(Long.class);
    }

    @PostConstruct
    private void completableFuture() {
        CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    //获取消息队列中的订单信息XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS sI >
                    try {
                        List<MapRecord<String, Object, Object>> tb = stringRedisTemplate.opsForStream().read(
                                Consumer.from("g1", "c1"), StreamReadOptions.empty().count(1)
                                        .block(Duration.ofSeconds(2)),
                                StreamOffset.create("stream.orders", ReadOffset.lastConsumed())
                        );
                        //判断消息是否为空，为空继续下一次循环
                        if (tb == null || tb.isEmpty()) {
                            continue;
                        }
                        //转化对象
                        MapRecord<String, Object, Object> order = tb.get(0);
                        Map<Object, Object> value = order.getValue();
                        TbVoucherOrder tbVoucherOrder = BeanUtil.fillBeanWithMap(value, new TbVoucherOrder(), true);

                        //不为空创建订单
                        createTbvoucherOrder(tbVoucherOrder);
                        //确认消息
                        stringRedisTemplate.opsForStream().acknowledge("stream.orders", "g1", order.getId());
                    } catch (Exception e) {
                        log.debug("订单出异常");
                        handlePeedingList();
                    }
                }
            }
        },poolTaskExecutor);
    }

    private void handlePeedingList() {
        while (true) {
            //获取pedinglist中的订单信息XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS sI >
            try {
                List<MapRecord<String, Object, Object>> tb = stringRedisTemplate.opsForStream().read(
                        Consumer.from("g1", "c1"), StreamReadOptions.empty().count(1)
                                .block(Duration.ofSeconds(2)),
                        StreamOffset.create("stream.orders", ReadOffset.from("0"))
                );
                //判断消息是否为空，为空继续下一次循环
                if (tb == null || tb.isEmpty()) {
                   break;
                }
                //转化对象
                MapRecord<String, Object, Object> order = tb.get(0);
                Map<Object, Object> value = order.getValue();
                TbVoucherOrder tbVoucherOrder = BeanUtil.fillBeanWithMap(value, new TbVoucherOrder(), true);

                //不为空创建订单
                createTbvoucherOrder(tbVoucherOrder);
                //确认消息
                stringRedisTemplate.opsForStream().acknowledge("stream.orders", "g1", order.getId());
            } catch (Exception e) {
                log.debug("订单pedding出异常");
            }
        }
    }


    private void createTbvoucherOrder(TbVoucherOrder voucherOrder) {

        //不同人不同锁，同样的id同一把锁。因为没一次toString返回的字符串多少不同引用，所以这里加上intern()方法来确定返回的是id地址
        RLock lock = redissonClient.getLock("lock:order:" + voucherOrder.getVoucherId());
        //传入时间应该比业务时间长一点,假如无参有默认值，但是不是重入锁
        boolean tryLock = lock.tryLock();
        if (!tryLock) {
            log.error("不允许重复下单");
            return;
        }
        //一人下一单
        long orderId;
        try {
            Integer count = query().eq("user_id", voucherOrder.getVoucherId()).eq("voucher_id",
                    voucherOrder.getVoucherId()).count();
            if (count > 0) {
                log.error("您已经抢购过该商品啦！请下次再来");
                return;
            }
            //扣减库存
            boolean updateNum = tbSeckillVoucherService.update().setSql("stock = stock - 1")
                    .eq("voucher_id", voucherOrder.getVoucherId()).
                    gt("stock", 0).update();
            if (!updateNum) {
                log.error("商品已经抢光了！！");
                return;
            }
            //创建订单
            TbVoucherOrder tbVoucherOrder = new TbVoucherOrder();
            //添加订单id，用户id，代金卷id
            orderId = myRedisIdWork.nextId("order");
            tbVoucherOrder.setId(voucherOrder.getId());
            tbVoucherOrder.setUserId(voucherOrder.getUserId());
            tbVoucherOrder.setVoucherId(voucherOrder.getVoucherId());
            save(tbVoucherOrder);
        } finally {
            //释放锁
            lock.unlock();
        }

    }

    @Override
    public Result seckillVoucher(Long vecheerId) {
        long orderId = myRedisIdWork.nextId("order");
        //执行Lua脚本
        Long userId = UserHolder.getUser().getId();

        Long r = stringRedisTemplate.
                execute(seckill_SCRIPT,
                        Collections.emptyList(), vecheerId.toString(),
                        userId.toString(), String.valueOf(orderId));
        //判断结果是否为零
        assert r != null;
        int value = r.intValue();
        if (value != 0) {
            //不为0没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        //为有购买资格，把下单信息保留进阻塞队列
        //生成订单id


        return Result.ok(orderId);
    }

//    @Override
//    public Result seckillVoucher(Long vecheerId) {
//        //查询优惠卷
//        TbSeckillVoucher tbSeckillVoucher = tbSeckillVoucherService.getById(vecheerId);
//        //判断秒杀是否开始
//        if (tbSeckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("活动还未开始");
//        }
//        //判断秒杀是否结束
//        if (tbSeckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("活动以结束");
//        }
//        if (tbSeckillVoucher.getStock() <= 0) {
//            return Result.fail("商品已经抢光了！！");
//        }
//
//        //返回订单
//        return createVoucherOrder(vecheerId);
//    }
//
//    @SneakyThrows
//    @Transactional()
//    public Result createVoucherOrder(Long vecheerId) {
//
//        Long userId = UserHolder.getUser().getId();
//        //不同人不同锁，同样的id同一把锁。因为没一次toString返回的字符串多少不同引用，所以这里加上intern()方法来确定返回的是id地址
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        //传入时间应该比业务时间长一点,假如无参有默认值，但是不是重入锁
//        boolean tryLock = lock.tryLock(2,  TimeUnit.SECONDS);
//        if (!tryLock){
//            return Result.fail("不允许重复下单");
//        }
//        //一人下一单
//        long orderId;
//        try {
//            Integer count = query().eq("user_id", userId).eq("voucher_id",
//                    vecheerId).count();
//            if (count > 0) {
//                return Result.fail("您已经抢购过该商品啦！请下次再来");
//            }
//            //扣减库存
//            boolean updateNum = tbSeckillVoucherService.update().setSql("stock = stock - 1")
//                    .eq("voucher_id", vecheerId).
//                    gt("stock", 0).update();
//            if (!updateNum) {
//                return Result.fail("商品已经抢光了！！");
//            }
//            //创建订单
//            TbVoucherOrder tbVoucherOrder = new TbVoucherOrder();
//            //添加订单id，用户id，代金卷id
//            orderId = myRedisIdWork.nextId("order");
//            tbVoucherOrder.setId(orderId);
//            tbVoucherOrder.setUserId(userId);
//            tbVoucherOrder.setVoucherId(vecheerId);
//            save(tbVoucherOrder);
//        } finally {
//            //释放锁
//            lock.unlock();
//        }
//        return Result.ok(orderId);
//    }

//    @Autowired
//    private MyRedisIdWork myRedisIdWork;
//
//    @Transactional()
//    public Result createVoucherOrder(Long vecheerId) {
//
//        Long userId = UserHolder.getUser().getId();
//        //不同人不同锁，同样的id同一把锁。因为没一次toString返回的字符串多少不同引用，所以这里加上intern()方法来确定返回的是id地址
//        MySimpleRedisLock mySimpleRedisLock = new MySimpleRedisLock(stringRedisTemplate, "order:"+userId);
//        //传入时间应该比业务时间长一点
//        boolean tryLock = mySimpleRedisLock.tryLock(12000);
//        if (!tryLock){
//            return Result.fail("不允许重复下单");
//        }
//        //一人下一单
//        long orderId;
//        try {
//            Integer count = query().eq("user_id", userId).eq("voucher_id",
//                    vecheerId).count();
//            if (count > 0) {
//                return Result.fail("您已经抢购过该商品啦！请下次再来");
//            }
//            //扣减库存
//            boolean updateNum = tbSeckillVoucherService.update().setSql("stock = stock - 1")
//                    .eq("voucher_id", vecheerId).
//                    gt("stock", 0).update();
//            if (!updateNum) {
//                return Result.fail("商品已经抢光了！！");
//            }
//            //创建订单
//            TbVoucherOrder tbVoucherOrder = new TbVoucherOrder();
//            //添加订单id，用户id，代金卷id
//            orderId = myRedisIdWork.nextId("order");
//            tbVoucherOrder.setId(orderId);
//            tbVoucherOrder.setUserId(userId);
//            tbVoucherOrder.setVoucherId(vecheerId);
//            save(tbVoucherOrder);
//        } finally {
//
//            //释放锁
//            mySimpleRedisLock.unlock();
//        }
//        return Result.ok(orderId);
//        }

}
