package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.ranking;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.ShopStats;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.ShopStatsRepository;

import java.util.List;

@Service
public class RankingService {

    private final ShopStatsRepository shopStatsRepository;
    private final ScoreCalculator scoreCalculator;
    private final StringRedisTemplate redis;

    public RankingService(ShopStatsRepository repo, ScoreCalculator calc, StringRedisTemplate redis) {
        this.shopStatsRepository = repo;
        this.scoreCalculator = calc;
        this.redis = redis;
    }

    // run every 5 minutes
    @Scheduled(fixedRateString = "${ranking.fixedRateMs:300000}")
    public void updateAllRanks() {
        List<ShopStats> all = shopStatsRepository.findAll();
        // group by city & category to create per-city-category sorted sets
        all.forEach(s -> {
            double score = scoreCalculator.score(s);
            String zkey = zKey(s.getCity(), s.getCategory());
            // use ZADD with score; score must be double - we'll convert to string
            redis.opsForZSet().add(zkey, s.getSellerId().toString(), score);
        });
    }

    public String zKey(String city, String category) {
        return String.format("shop_rank:%s:%s", city.toLowerCase(), category.toLowerCase());
    }
}
