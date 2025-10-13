package com.example.Contract_review.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 主页控制器
 *
 * 处理首页访问请求
 */
@Controller
public class HomeController {

    /**
     * 根路径访问,返回index.html
     */
    @GetMapping("/")
    public String index() {
        return "forward:/index.html";
    }

    /**
     * 直接访问home也返回index.html
     */
    @GetMapping("/home")
    public String home() {
        return "forward:/index.html";
    }
}
