package com.nexters.api.controller

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/og")
class OgTestController {

    @GetMapping("/test")
    fun ogTestPage(): String {
        return "og-test"
    }
}