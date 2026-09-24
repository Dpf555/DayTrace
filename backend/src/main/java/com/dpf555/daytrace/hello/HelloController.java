package com.dpf555.daytrace.hello;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController    //将方法返回值作为HTTP响应内容
public class HelloController {
    @GetMapping("/api/v1/hello")   //将GET请求映射到方法
    public HelloResponse Hello(@RequestParam(defaultValue = "DayTrace") String name, @RequestParam String message) {
        return new HelloResponse(name,message);
    }

    @PostMapping("/api/v1/hello")
    public HelloResponse greet(@Valid @RequestBody HelloRequest request) {
        String name = request.getName();
        String message = "Hello," + name + "!";
        return new HelloResponse(name,message);
    }
}
