# SR-Backend Home Assessment

## Project Overview

This repository demonstrates an end-to-end streaming data pipeline using Apache Kafka, Apache Flink, and Docker Compose. Order events are read from a static JSON file, ingested into a Kafka topic (`orders`), processed in 1-minute tumbling windows by a Flink streaming job (summing order amounts per country), and the aggregated results are written back to another Kafka topic (`processed-orders`).

## Key Components

* **Docker Compose**

  * `docker-compose-kafka.yml`: Kafka and ZooKeeper services
  * `docker-compose-flink.yml`: Flink JobManager and TaskManager services
* **Python Producer** (`data-producer.py`)

  * Reads `orders.json` and publishes events to the Kafka `orders` topic in real time
* **Flink Job** (`OrderAggregationJob.java`)

  * Consumes from `orders`, aggregates totals per country in 1-minute windows, and writes JSON aggregates to `processed-orders`
* **Model Classes**

  * `Order.java`: POJO for incoming order events
  * `OrderAggregate.java`: POJO for aggregated results
* **Maven Build** (`pom.xml`)

  * Manages dependencies (Flink, Kafka connector, Jackson, SLF4J) and creates a fat-jar via the Maven Shade Plugin

## Environment & Prerequisites

* **Host**: macOS (Apple M1) with Docker Desktop
* **Docker** & **Docker Compose**
* **Java** 8+ and **Maven** 3.6+
* **Python** 3.x and **pip3**
* **Local Ports**:

  * ZooKeeper: `2181`
  * Kafka external: `9092`
  * Kafka internal: `9093`
  * Flink REST UI: `8081`

## Repository Structure

```
├── docker-compose-kafka.yml       # Kafka & ZooKeeper configuration
├── docker-compose-flink.yml       # Flink JobManager & TaskManager configuration
├── data-producer.py               # Python script to produce orders to Kafka
├── orders.json                    # Sample order events (one JSON object per line)
├── streaming_setup.sh             # Wrapper script to build and run services
├── requirements.txt               # Python dependencies
└── flink-job/                     # Maven project for Flink job
    ├── pom.xml                    # Maven configuration
    └── src/main/java/com/company/flink
        ├── OrderAggregationJob.java
        └── model
            ├── Order.java
            └── OrderAggregate.java
```

## Setup & Execution

This section describes two ways to launch and run the streaming pipeline: an **automated** approach using our `streaming_setup.sh` helper script, and a **manual** approach for full control and visibility.

### 🔧 Automated Pipeline

Simply call the helper script with one of three modes:

```bash
# 1. Start ZooKeeper & Kafka only
./streaming_setup.sh kafka

# 2. Start the full pipeline (Kafka + Flink), build & deploy the job, and kick off data production
./streaming_setup.sh flink

# 3. Tear down all services
./streaming_setup.sh down
```

The script will:

1. Validate that required tools (`docker-compose`, `mvn`, `pip3`, `python3`, `nc`) are on your PATH.
2. Bring up or down the Docker Compose stacks for Kafka and Flink.
3. Wait for relevant ports (Kafka external: `9092`, Flink REST UI: `8081`) to become available.
4. Build the Flink fat-jar via Maven.
5. Copy the resulting JAR into the Flink JobManager container.
6. Submit the job in detached mode so your terminal is free immediately.
7. Install Python dependencies and start the real-time data producer.

After running in `flink` mode, you can verify:

```bash
# Raw orders
docker exec kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic orders --from-beginning --max-messages 6

# Aggregated output
docker exec kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic processed-orders --from-beginning --property print.value=true

# Flink Web UI
open http://localhost:8081
```

### 📝 Manual Pipeline

If you prefer step‑by‑step control, follow these commands:

1. **Tear down any running containers**

   ```bash
   docker-compose \
     -f docker-compose-kafka.yml \
     -f docker-compose-flink.yml \
     down --remove-orphans
   ```

2. **Build the Flink job JAR**

   ```bash
   cd flink-job
   mvn clean package -DskipTests
   cd ..
   ```

3. **Start ZooKeeper, Kafka, and Flink**

   ```bash
   docker-compose \
     -f docker-compose-kafka.yml \
     -f docker-compose-flink.yml \
     up -d
   ```

   Wait \~30 seconds for all services to initialize.

4. **Verify logs for health**

   ```bash
   docker logs -f zookeeper
   docker logs -f kafka       # look for "Kafka Server started"
   docker logs -f flink-jobmanager  # look for REST UI message
   docker logs -f flink-taskmanager # look for successful registration
   ```

5. **Deploy JAR into Flink**

   ```bash
   docker exec flink-jobmanager mkdir -p /opt/flink/usrlib
   docker cp flink-job/target/flink-job-1.0-SNAPSHOT.jar \
     flink-jobmanager:/opt/flink/usrlib/
   ```

6. **Submit your Flink job**

   ```bash
   docker exec flink-jobmanager flink run \
     -m localhost:8081 \
     -c com.company.flink.OrderAggregationJob \
     /opt/flink/usrlib/flink-job-1.0-SNAPSHOT.jar \
     --bootstrap.servers kafka:9093 \
     --orders.topic orders \
     --output.topic processed-orders
   ```

   You should see: `Job has been submitted with JobID ...`.

7. **Run the Python producer**

   ```bash
   pip3 install -r requirements.txt
   python3 data-producer.py
   ```

8. **Inspect topics**

   ```bash
   # Raw events
   docker exec kafka kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic orders --from-beginning --max-messages 6

   # Aggregated results
   docker exec kafka kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic processed-orders --from-beginning --property print.value=true
   ```

---

By choosing the automated script you save time and reduce manual commands; the manual steps give you deeper visibility and control over each stage of the pipeline.
