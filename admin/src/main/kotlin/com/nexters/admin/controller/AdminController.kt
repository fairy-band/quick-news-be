package com.nexters.admin.controller

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class AdminController {
    @GetMapping("/")
    fun index(): String = "contents"

    @GetMapping("/metrics")
    fun metrics(): String = "metrics"

    @GetMapping("/keywords")
    fun keywords(): String = "keywords"

    @GetMapping("/contents")
    fun contents(): String = "contents"

    @GetMapping("/recommendations")
    fun recommendations(): String = "recommendations"
}
