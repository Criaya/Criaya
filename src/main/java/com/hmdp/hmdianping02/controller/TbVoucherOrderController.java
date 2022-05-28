package com.hmdp.hmdianping02.controller;


import com.hmdp.hmdianping02.dto.Result;
import com.hmdp.hmdianping02.entity.TbVoucher;
import com.hmdp.hmdianping02.service.impl.TbVoucherOrderServiceImpl;
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
@RequestMapping("/voucher-order")
public class TbVoucherOrderController {
    @Autowired
    private TbVoucherOrderServiceImpl tbVoucherOrderService;
    @Autowired
    private TbVoucherServiceImpl tbVoucherService;
    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id")Long vecheerId){

        return tbVoucherOrderService.seckillVoucher(vecheerId);
    }

}
