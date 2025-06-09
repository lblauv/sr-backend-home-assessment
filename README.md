# Streaming Pipeline Assessment

## Overview

In this assessment you are to build a stream processing pipeline that:
- **Ingests** order events from a Kafka topic named `orders`.
- **Aggregates** the total order amount per country in 1-minute tumbling windows.
- **Publishes** the aggregated results to a new Kafka topic named `processed-orders`, showing the total amount per country per minute.

## What’s Provided

- **Docker Compose Files:**
  - `docker-compose-kafka.yml` – Sets up a Kafka cluster (with ZooKeeper).
  - `docker-compose-flink.yml` – (Optional) Sets up an Apache Flink cluster.

- **Infrastructure Setup Script:**
  - `streaming_setup.sh` – A shell script that starts the necessary containers (Kafka, and optionally Flink) and launches a data producer.

- **Data Producer:**
  - `data-producer.py` – A Python script that reads order events from `orders.json` and publishes them to the `orders` Kafka topic.
  
- **Sample Data File:**
  - `orders.json` – A sample file containing order events (one JSON object per line) for testing your stream processing.

## Prerequisites

- **Docker** – Ensure you have Docker installed and running.
- **Python3** – Ensure Python3 is installed.
