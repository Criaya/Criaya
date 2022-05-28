package com.hmdp.hmdianping02.controller;


import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.hmdianping02.dto.Result;
import com.hmdp.hmdianping02.entity.TbShop;
import com.hmdp.hmdianping02.service.impl.TbShopServiceImpl;
import com.hmdp.hmdianping02.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.sql.ResultSet;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author sangeng
 * @since 2022-04-17
 */
@RestController
@RequestMapping("/shop")
public class TbShopController {
    @Autowired
    private TbShopServiceImpl shopService;
    @GetMapping("/{id}")
    public Result queryShopById(@PathVariable("id")Long id){
        return shopService.queryShopById(id);
    }


    @PutMapping
    public Result updateShop(@RequestBody TbShop tbShop){
        return shopService.updateShop(tbShop);
    }
    @GetMapping("/of/type")
    public Result queryShopByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "x", required = false) Double x,
            @RequestParam(value = "y", required = false) Double y
    ) {
        return shopService.queryShopByType(typeId, current, x, y);
    }
    @GetMapping("/of/name")
    public Result queryShopByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 根据类型分页查询
        Page<TbShop> page = shopService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 返回数据
        return Result.ok(page.getRecords());
    }
}
