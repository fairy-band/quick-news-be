package com.nexters.admin.controller

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class AdminController {
    @GetMapping("/")
    fun index(): String = "index"

    @GetMapping("/metrics")
    fun metrics(): String = "metrics"
}
