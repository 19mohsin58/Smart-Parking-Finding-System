package com.example.SPFS.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class SpfsController {

    @GetMapping("/")
    @ResponseBody
    public String index() {
        return "Welcome — Home Page is Mohsin!";
    }
}
