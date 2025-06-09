package com.company.flink;

import com.company.flink.model.Order;
import com.company.flink.model.OrderAggregate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer;
import org.apache.flink.util.Collector;

import java.time.Duration;
import java.time.Instant;
import java.util.Properties;

public class OrderAggregationJob {
    public static void main(String[] args) throws Exception {
        // 1. Parse command-line parameters
        ParameterTool params = ParameterTool.fromArgs(args);
        String brokers = params.get("bootstrap.servers", "localhost:9092");
        String inputTopic = params.get("orders.topic", "orders");
        String outputTopic = params.get("output.topic", "processed-orders");

        // 2. Set up the Flink execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.getConfig().setGlobalJobParameters(params);
        env.setParallelism(1);

        // 3. Configure Kafka consumer properties
        Properties consumerProps = new Properties();
        consumerProps.setProperty("bootstrap.servers", brokers);
        consumerProps.setProperty("group.id", "flink-order-agg-group");
        FlinkKafkaConsumer<String> kafkaConsumer = new FlinkKafkaConsumer<>(
            inputTopic,
            new SimpleStringSchema(),
            consumerProps
        );
        kafkaConsumer.setStartFromEarliest();

        // 4. Define watermark strategy for event-time processing (5s out-of-order)
        WatermarkStrategy<Order> wmStrategy = WatermarkStrategy
            .<Order>forBoundedOutOfOrderness(Duration.ofSeconds(5))
            .withTimestampAssigner(new SerializableTimestampAssigner<Order>() {
                @Override
                public long extractTimestamp(Order element, long recordTimestamp) {
                    try {
                        return Instant.parse(element.getTimestamp()).toEpochMilli();
                    } catch (Exception e) {
                        return Instant.now().toEpochMilli();
                    }
                }
            });

        // 5a. Source: read raw JSON strings from Kafka
        DataStream<String> rawStream = env.addSource(kafkaConsumer);

        // 5b. Map JSON into Order POJOs and assign timestamps/watermarks
        DataStream<Order> orders = rawStream
            .map(json -> {
                ObjectMapper mapper = new ObjectMapper();
                try {
                    return mapper.readValue(json, Order.class);
                } catch (JsonProcessingException e) {
                    Order bad = new Order();
                    bad.setOrderId("ERROR");
                    bad.setCountry("UNKNOWN");
                    bad.setTimestamp(Instant.now().toString());
                    bad.setAmount(0.0);
                    return bad;
                }
            })
            .assignTimestampsAndWatermarks(wmStrategy);

        // 5c. Key the stream by country
        KeyedStream<Order, String> keyedByCountry = orders.keyBy(Order::getCountry);

        // 5d. Windowed aggregation: 1-minute tumbling windows
        DataStream<OrderAggregate> aggregated = keyedByCountry
            .window(TumblingEventTimeWindows.of(Time.minutes(1)))
            .reduce(
                (o1, o2) -> {
                    Order sum = new Order();
                    sum.setOrderId(o1.getOrderId());
                    sum.setTimestamp(o1.getTimestamp());
                    sum.setCountry(o1.getCountry());
                    sum.setAmount(o1.getAmount() + o2.getAmount());
                    return sum;
                },
                new ProcessWindowFunction<Order, OrderAggregate, String, TimeWindow>() {
                    @Override
                    public void process(String country,
                                        Context context,
                                        Iterable<Order> elements,
                                        Collector<OrderAggregate> out) {
                        Order summed = elements.iterator().next();
                        out.collect(new OrderAggregate(
                            country,
                            context.window().getStart(),
                            context.window().getEnd(),
                            summed.getAmount()
                        ));
                    }
                }
            );

        // 5e. Convert aggregated POJOs back to JSON strings
        DataStream<String> resultJson = aggregated
            .map(agg -> new ObjectMapper().writeValueAsString(agg));

        // 6. Configure Kafka producer properties
        Properties producerProps = new Properties();
        producerProps.setProperty("bootstrap.servers", brokers);

        // Use explicit type argument to satisfy compiler
        FlinkKafkaProducer<String> kafkaProducer = new FlinkKafkaProducer<>(
            outputTopic,
            new SimpleStringSchema(),
            producerProps
        );

        // 7. Sink the result back to Kafka
        resultJson.addSink(kafkaProducer);

        // 8. Execute the Flink job
        env.execute("Order Aggregation Job");
    }
}
