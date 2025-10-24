package com.example.Contract_review.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;

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
    public ResponseEntity<byte[]> index() {
        try {
            Resource resource = new ClassPathResource("static/index.html");
            if (resource.exists()) {
                // 使用 getInputStream() 而不是 Paths.get()
                // 这样可以兼容 JAR 包中的资源
                byte[] content = resource.getInputStream().readAllBytes();
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.TEXT_HTML);
                headers.setContentLength(content.length);
                return new ResponseEntity<>(content, headers, HttpStatus.OK);
            } else {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
        } catch (IOException e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 直接访问home也返回index.html
     */
    @GetMapping("/home")
    public ResponseEntity<byte[]> home() {
        return index();
    }
}
