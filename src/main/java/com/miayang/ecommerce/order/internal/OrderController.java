package com.miayang.ecommerce.order.internal;

import com.miayang.ecommerce.order.internal.OrderDtos.OrderResponse;
import com.miayang.ecommerce.order.internal.OrderDtos.OrderSummary;
import com.miayang.ecommerce.order.internal.OrderDtos.PlaceOrderRequest;
import com.miayang.ecommerce.shared.security.CurrentUser;
import com.miayang.ecommerce.shared.web.PageResponse;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** All order endpoints need a logged-in user (SecurityConfig: anyRequest().authenticated()). */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse place(@Valid @RequestBody PlaceOrderRequest request, Authentication authentication) {
        return orderService.placeOrder(CurrentUser.from(authentication).id(), request);
    }

    /** The caller's own orders, newest first. */
    @GetMapping
    public PageResponse<OrderSummary> myOrders(
            Authentication authentication,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return orderService.myOrders(CurrentUser.from(authentication).id(), pageable);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id, Authentication authentication) {
        return orderService.getOrder(id, CurrentUser.from(authentication));
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable Long id, Authentication authentication) {
        return orderService.cancel(id, CurrentUser.from(authentication));
    }
}
