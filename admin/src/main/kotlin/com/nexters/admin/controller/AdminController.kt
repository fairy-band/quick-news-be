package com.nexters.admin.controller

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.servlet.view.RedirectView

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
}
