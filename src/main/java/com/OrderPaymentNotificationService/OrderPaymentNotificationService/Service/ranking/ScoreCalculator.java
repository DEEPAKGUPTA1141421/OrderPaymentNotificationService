package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.ranking;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Model.ShopStats;

public class ScoreCalculator {
    // weights - tune these values
    private final double ORDER_WEIGHT = 0.5;
    private final double RATING_WEIGHT = 0.3;
    private final double RECENCY_WEIGHT = 0.2;// naive normalization; replace with percentile/robust normalization for
                                              // real systems

    private double normalizeOrderCount(long count) {
        // example: log scale
        return Math.log1p(count);
    }

    private double normalizeRating(double rating) {
        // rating assumed 0..5
        return rating / 5.0;
    }

    private double normalizeRecency(long recent) {
        return Math.log1p(recent);
    }

    public double score(ShopStats s) {
        double o = normalizeOrderCount(s.getOrderCount());
        double r = normalizeRating(s.getAvgRating());
        double rec = normalizeRecency(s.getRecentOrders());

        // combine
        return (o * ORDER_WEIGHT) + (r * RATING_WEIGHT) + (rec * RECENCY_WEIGHT);
    }
}
