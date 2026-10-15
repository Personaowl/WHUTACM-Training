package com.whut.training.controller;

import com.whut.training.context.UserContext;
import com.whut.training.domain.dto.HomeOverviewResponse;
import com.whut.training.domain.entity.User;
import com.whut.training.exception.BusinessException;
import com.whut.training.service.HomeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/home")
public class HomeController {

    private final HomeService homeService;

    public HomeController(HomeService homeService) {
        this.homeService = homeService;
    }

    @GetMapping
    public HomeOverviewResponse overview(@RequestParam(name = "top", defaultValue = "10") int top) {
        User user = UserContext.getCurrentUser();
        if (user == null) throw new BusinessException(401, "unauthorized");
        // Authentication has already read this user from the database, so no extra query is needed.
        return HomeOverviewResponse.from(homeService.getOverview(top), user);
    }
}
