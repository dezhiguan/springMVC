package com.gdz.controller;

import com.gdz.anntation.GdzAutowired;
import com.gdz.anntation.GdzController;
import com.gdz.anntation.GdzReqeustMapping;
import com.gdz.anntation.GdzRequestParam;
import com.gdz.service.UserService;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @Author: guandezhi
 * @Date: 2019/1/20 20:40
 */
@Slf4j
@GdzController
@GdzReqeustMapping("/user")
public class UserController {

    @GdzAutowired
    private UserService userService;


    @GdzReqeustMapping("/addUser")
    public void addUser(HttpServletResponse response, @GdzRequestParam String username) throws IOException {

        response.getWriter().write(userService.addUser(username));
    }

}
