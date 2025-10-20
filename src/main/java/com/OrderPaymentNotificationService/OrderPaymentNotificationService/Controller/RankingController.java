package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/rankings")
public class RankingController {

    private final StringRedisTemplate redis;

    public RankingController(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @GetMapping("/top")
    public Set<String> top(@RequestParam String city, @RequestParam String category,
            @RequestParam(defaultValue = "10") int n) {
        String key = String.format("shop_rank:%s:%s", city.toLowerCase(), category.toLowerCase());
        Set<ZSetOperations.TypedTuple<String>> tuples = redis.opsForZSet().reverseRangeWithScores(key, 0, n - 1);
        if (tuples == null)
            return Set.of();
        return tuples.stream()
                .map(t -> t.getValue() + ":" + t.getScore()) // returns "sellerId:score" — adapt to return full Seller
                                                             // objects by fetching from ProductClientService if needed
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
