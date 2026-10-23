package com.whut.training.controller;

import com.whut.training.common.ApiResponse;
import com.whut.training.context.UserContext;
import com.whut.training.domain.dto.UpsolvePlanRequest;
import com.whut.training.domain.dto.UpsolvePlanView;
import com.whut.training.exception.BusinessException;
import com.whut.training.service.UpsolvePlanService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/upsolve-plans")
public class UpsolvePlanController {
    private final UpsolvePlanService service;
    public UpsolvePlanController(UpsolvePlanService service) { this.service = service; }
    @GetMapping
    public ApiResponse<UpsolvePlanService.Page> list(@RequestParam(defaultValue="false") boolean completed,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size) {
        return ApiResponse.ok(service.list(userId(),completed,page,size));
    }
    @PostMapping
    public ApiResponse<UpsolvePlanView> create(@Valid @RequestBody UpsolvePlanRequest request) {
        return ApiResponse.ok(service.save(userId(),null,request));
    }
    @PatchMapping("/{id}")
    public ApiResponse<UpsolvePlanView> edit(@PathVariable long id,@Valid @RequestBody UpsolvePlanRequest request) {
        return ApiResponse.ok(service.save(userId(),id,request));
    }
    @PatchMapping("/{id}/completion")
    public ApiResponse<UpsolvePlanView> complete(@PathVariable long id,@Valid @RequestBody Completion request) {
        return ApiResponse.ok(service.complete(userId(),id,request.completed()));
    }
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) { service.delete(userId(),id); return ApiResponse.ok(); }
    private long userId() {
        var user=UserContext.getCurrentUser();
        if(user==null || user.getId()==null) throw new BusinessException(401,"unauthorized");
        return user.getId();
    }
    public record Completion(@NotNull Boolean completed) { }
}
