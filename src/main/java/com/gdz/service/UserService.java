package com.gdz.service;

import com.gdz.anntation.GdzService;

/**
 * @Author: guandezhi
 * @Date: 2019/1/20 20:42
 */
@GdzService("userService")
public class UserService {

    public String addUser(String username) {
        return "addUser " + username + " success";
    }

}
