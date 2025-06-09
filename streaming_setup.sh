#!/bin/bash
if [ "$#" -ne 1 ]; then
  echo "Usage: ./streaming_setup.sh [kafka|flink]"
  exit 1
fi

MODE=$1

if [ "$MODE" == "kafka" ]; then
  echo "Starting Kafka cluster..."
  docker-compose -f docker-compose-kafka.yml up -d
elif [ "$MODE" == "flink" ]; then
  echo "Starting Flink cluster..."
  docker-compose -f docker-compose-kafka.yml -f docker-compose-flink.yml up -d
else
  echo "Unknown mode: $MODE. Use 'kafka' or 'flink'."
  exit 1
fi

echo "Waiting for clusters to initialize (approx. 20 seconds)..."
sleep 20

echo "Starting data producer to generate order events..."
pip3 install -r requirements.txt
python3 data-producer.py

echo "Infrastructure setup complete. Check your Docker containers and logs for activity."
