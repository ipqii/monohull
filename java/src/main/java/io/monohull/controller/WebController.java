package io.monohull.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {

    @GetMapping(value = {"/", "/login", "/environments/**", "/config/**", "/pipelines/**"})
    public String forward() {
        return "forward:/index.html";
    }
}
