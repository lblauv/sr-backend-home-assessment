package com.company.flink;

import com.company.flink.model.Order;
import com.company.flink.model.OrderAggregate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import static org.junit.Assert.*;

public class OrderAndAggregateTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testJsonToOrderMapping() throws Exception {
        String json = "{\"order_id\":\"123\",\"timestamp\":\"2025-06-08T10:00:00Z\",\"country\":\"US\",\"amount\":42.5}";
        Order o = mapper.readValue(json, Order.class);

        assertEquals("123", o.getOrderId());
        assertEquals("2025-06-08T10:00:00Z", o.getTimestamp());
        assertEquals("US", o.getCountry());
        assertEquals(42.5, o.getAmount(), 0.0001);
    }

    @Test
    public void testOrderToJsonMapping() throws Exception {
        Order o = new Order("abc","2025-06-08T11:00:00Z","DE",99.99);
        String serialized = mapper.writeValueAsString(o);
        Order roundTrip = mapper.readValue(serialized, Order.class);

        assertEquals(o.getOrderId(), roundTrip.getOrderId());
        assertEquals(o.getTimestamp(), roundTrip.getTimestamp());
        assertEquals(o.getCountry(),   roundTrip.getCountry());
        assertEquals(o.getAmount(),    roundTrip.getAmount(), 0.0001);
    }

    @Test
    public void testAggregateSerialization() throws Exception {
        OrderAggregate agg = new OrderAggregate("FR", 1000L, 2000L, 123.45);
        String json = mapper.writeValueAsString(agg);

        assertTrue(json.contains("\"country\":\"FR\""));
        assertTrue(json.contains("\"windowStart\":1000"));
        assertTrue(json.contains("\"windowEnd\":2000"));
        assertTrue(json.contains("\"totalAmount\":123.45"));

        OrderAggregate round = mapper.readValue(json, OrderAggregate.class);
        assertEquals("FR", round.getCountry());
        assertEquals(1000L, round.getWindowStart());
        assertEquals(2000L, round.getWindowEnd());
        assertEquals(123.45, round.getTotalAmount(), 0.0001);
    }
}
