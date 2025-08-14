package com.nexters.admin.controller

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@Profile("prod", "dev")
@RequestMapping("/rss")
class RssManagementController {
    
    @GetMapping("/management")
    fun getRssManagementPage(): String = "rss-management"
}