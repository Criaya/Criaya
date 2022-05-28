package com.hmdp.hmdianping02.controller;


import com.hmdp.hmdianping02.dto.Result;
import com.hmdp.hmdianping02.entity.TbVoucher;
import com.hmdp.hmdianping02.service.impl.TbVoucherServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author sangeng
 * @since 2022-04-17
 */
@RestController
@RequestMapping("/voucher")
public class TbVoucherController {
    @Autowired
    private TbVoucherServiceImpl tbVoucherService;
    @PostMapping("seckill")
    public Result addSeckillVoucher(@RequestBody TbVoucher tbVoucher){
        tbVoucherService.addSeckillVoucher(tbVoucher);
        return Result.ok(tbVoucher.getId());
    }
    /**
     * 新增普通券
     * @param voucher 优惠券信息
     * @return 优惠券id
     */
    @PostMapping
    public Result addVoucher(@RequestBody TbVoucher voucher) {
        tbVoucherService.save(voucher);
        return Result.ok(voucher.getId());
    }


    /**
     * 查询店铺的优惠券列表
     * @param shopId 店铺id
     * @return 优惠券列表
     */
    @GetMapping("/list/{shopId}")
    public Result queryVoucherOfShop(@PathVariable("shopId") Long shopId) {
        return tbVoucherService.queryVoucherOfShop(shopId);
    }
}
