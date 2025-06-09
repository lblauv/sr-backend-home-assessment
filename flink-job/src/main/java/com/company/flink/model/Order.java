package com.company.flink.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POJO representing an incoming order event.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Order {

    @JsonProperty("order_id")
    private String orderId;

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("country")
    private String country;

    @JsonProperty("amount")
    private double amount;

    // Default constructor for Jackson
    public Order() {}

    public Order(String orderId, String timestamp, String country, double amount) {
        this.orderId = orderId;
        this.timestamp = timestamp;
        this.country = country;
        this.amount = amount;
    }

    // Getters and setters
    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }
}
