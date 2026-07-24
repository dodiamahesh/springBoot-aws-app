package com.example.springbootawsapp;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/hello")
    public String hello() {
        return "Hello Dhairyaraj Dodia from AWS!";
    }

    @GetMapping("/")
    public String home() {
        return "Spring Boot app is running by Mahesh";
    }
}
