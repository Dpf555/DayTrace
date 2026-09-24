package com.dpf555.daytrace.hello;

// 服务端返回什么

public class HelloResponse {
    private String name;
    private String message;

    public HelloResponse(String name, String message) {
        this.name = name;
        this.message = message;
    }

    public String getName() {
        return name;
    }

    public String getMessage() {
        return message;
    }
}
