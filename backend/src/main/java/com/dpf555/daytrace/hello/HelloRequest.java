package com.dpf555.daytrace.hello;

//客户端提交什么

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class HelloRequest {

    @NotBlank(message = "姓名不能为空")
    @Size(max = 30, message = "姓名不能超过30个字符")
    private String name;

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
