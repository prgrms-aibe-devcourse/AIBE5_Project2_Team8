package com.knoc.senior.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SeniorApplyController {

    @GetMapping("/senior/apply")
    public String apply() {
        return "senior/apply";
    }

    @GetMapping("/senior/profile-setup")
    public String profileSetup() {
        return "profile_setup";
    }
}
