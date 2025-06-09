package com.company.flink.model;

/**
 * POJO representing the aggregated output for a time window per country.
 */
public class OrderAggregate {
    private String country;
    private long windowStart;
    private long windowEnd;
    private double totalAmount;

    /**
     * No-arg constructor for Jackson
     */
    public OrderAggregate() {}

    /**
     * All-args constructor for convenience
     */
    public OrderAggregate(String country, long windowStart, long windowEnd, double totalAmount) {
        this.country = country;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.totalAmount = totalAmount;
    }

    // Getters and setters
    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public long getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(long windowStart) {
        this.windowStart = windowStart;
    }

    public long getWindowEnd() {
        return windowEnd;
    }

    public void setWindowEnd(long windowEnd) {
        this.windowEnd = windowEnd;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(double totalAmount) {
        this.totalAmount = totalAmount;
    }
}
