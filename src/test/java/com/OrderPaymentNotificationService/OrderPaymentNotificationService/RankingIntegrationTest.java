package com.OrderPaymentNotificationService.OrderPaymentNotificationService;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.DTO.ranking.OrderPlacedEvent;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Repository.ShopStatsRepository;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.ranking.OrderProducer;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.ranking.RankingService;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = { "orders.events" })
@TestPropertySource(properties = {
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "ranking.fixedRateMs=60000" // reduce during test if scheduler runs
})
public class RankingIntegrationTest {

    @Autowired
    private OrderProducer orderProducer;

    @Autowired
    private ShopStatsRepository shopStatsRepository;

    @Autowired
    private RankingService rankingService;

    @Autowired
    private StringRedisTemplate redis;

    @AfterEach
    void clean() {
        shopStatsRepository.deleteAll();
        redis.getConnectionFactory().getConnection().flushAll();
    }

    @Test
    void whenOrdersProduced_thenRankingUpdated() throws Exception {
        UUID sellerA = UUID.randomUUID();
        UUID sellerB = UUID.randomUUID();

        // produce events: sellerA 5 orders, sellerB 2 orders
        for (int i = 0; i < 5; i++) {
            orderProducer.publish(
                    new OrderPlacedEvent(UUID.randomUUID(), sellerA, "Bangalore", "ELECTRONICS", Instant.now()));
        }
        for (int i = 0; i < 2; i++) {
            orderProducer.publish(
                    new OrderPlacedEvent(UUID.randomUUID(), sellerB, "Bangalore", "ELECTRONICS", Instant.now()));
        }

        // wait for consumer to process (simple sleep; in production use Awaitility or
        // latches)
        Thread.sleep(2000);

        // force ranking computation (call service directly)
        rankingService.updateAllRanks();

        String zkey = "shop_rank:bangalore:electronics";
        Set<String> top = redis.opsForZSet().reverseRangeWithScores(zkey, 0, 10)
                .stream()
                .map(t -> t.getValue() + ":" + t.getScore())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(top).anyMatch(s -> s.startsWith(sellerA.toString()));
        assertThat(top).anyMatch(s -> s.startsWith(sellerB.toString()));

        // verify sellerA has a higher score than sellerB
        Double scoreA = redis.opsForZSet().score(zkey, sellerA.toString());
        Double scoreB = redis.opsForZSet().score(zkey, sellerB.toString());

        assertThat(scoreA).isGreaterThan(scoreB);
    }
}
