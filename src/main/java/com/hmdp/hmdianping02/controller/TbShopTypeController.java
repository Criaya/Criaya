package com.hmdp.hmdianping02.controller;


import com.hmdp.hmdianping02.dto.Result;
import com.hmdp.hmdianping02.entity.TbShopType;
import com.hmdp.hmdianping02.service.impl.TbShopTypeServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author sangeng
 * @since 2022-04-17
 */
@RestController
@RequestMapping("/shop-type")
public class TbShopTypeController {
    @Autowired
    private TbShopTypeServiceImpl tbShopTypeService;
    @GetMapping("list")
    public Result queryTypeList(){
        List<TbShopType> typeList = tbShopTypeService.query()
                .orderByAsc("sort").list();
        return tbShopTypeService.quryTypeList();
    }
}
