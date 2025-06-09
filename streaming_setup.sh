#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# streaming_setup.sh
#
# Automates your end-to-end streaming pipeline on Docker + Flink + Kafka.
#
# Prerequisites:
#   - docker-compose
#   - mvn (Maven)
#   - pip3 & python3
#   - netcat (nc)
#
# Usage:
#   ./streaming_setup.sh kafka   # Start ZooKeeper & Kafka only
#   ./streaming_setup.sh flink   # Tear down/up Kafka+Flink, build & deploy Flink job, start producer
#   ./streaming_setup.sh down    # Tear down all services
# ==============================================================================

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FLINK_JAR="flink-job/target/flink-job-1.0-SNAPSHOT.jar"
JM_LIB_DIR="/opt/flink/usrlib"

# ------------------------------------------------------------------------------
# Verify required commands are on PATH
# ------------------------------------------------------------------------------
check_prereqs() {
  for cmd in docker-compose mvn pip3 python3 nc; do
    command -v "$cmd" >/dev/null 2>&1 \
      || { echo "❌ Required command '$cmd' not found."; exit 1; }
  done
}

# ------------------------------------------------------------------------------
# Tear down all running containers (Kafka + Flink)
# ------------------------------------------------------------------------------
down_all() {
  echo "🧹 Tearing down ZooKeeper, Kafka, Flink..."
  docker-compose \
    -f docker-compose-kafka.yml \
    -f docker-compose-flink.yml \
    down --remove-orphans
}

# ------------------------------------------------------------------------------
# Start ZooKeeper & Kafka only
# ------------------------------------------------------------------------------
start_kafka() {
  echo "🚀 Starting ZooKeeper & Kafka..."
  docker-compose -f docker-compose-kafka.yml up -d
}

# ------------------------------------------------------------------------------
# Start Kafka + Flink JobManager & TaskManager
# ------------------------------------------------------------------------------
start_flink() {
  echo "🚀 Starting Kafka + Flink JobManager & TaskManager..."
  docker-compose \
    -f docker-compose-kafka.yml \
    -f docker-compose-flink.yml \
    up -d
}

# ------------------------------------------------------------------------------
# Wait until a TCP port is accepting connections
# ------------------------------------------------------------------------------
wait_for_port() {
  local host=$1 port=$2
  printf "⏱  Waiting for %s:%s " "$host" "$port"
  until nc -z "$host" "$port"; do
    printf "."; sleep 1
  done
  echo " ok"
}

# ------------------------------------------------------------------------------
# Build the Flink fat-jar via Maven
# ------------------------------------------------------------------------------
build_flink_jar() {
  echo "📦 Building Flink job JAR..."
  pushd flink-job >/dev/null
    mvn clean package -DskipTests
  popd  >/dev/null
}

# ------------------------------------------------------------------------------
# Deploy the fat-jar into the Flink JobManager container
# ------------------------------------------------------------------------------
deploy_jar() {
  echo "📤 Deploying JAR into Flink JobManager..."
  if [[ ! -f "${ROOT_DIR}/${FLINK_JAR}" ]]; then
    echo "❌ JAR not found: ${ROOT_DIR}/${FLINK_JAR}"
    exit 1
  fi
  docker exec flink-jobmanager mkdir -p "${JM_LIB_DIR}"
  docker cp "${ROOT_DIR}/${FLINK_JAR}" flink-jobmanager:"${JM_LIB_DIR}/"
}

# ------------------------------------------------------------------------------
# Submit the Flink job in background (docker exec -d) so script does not hang
# ------------------------------------------------------------------------------
submit_job() {
  echo "▶️  Submitting Flink job to JobManager..."
  # Use docker exec -d to detach the submission command immediately
  docker exec -d flink-jobmanager flink run \
    -m localhost:8081 \
    -c com.company.flink.OrderAggregationJob \
    "${JM_LIB_DIR}/$(basename "${FLINK_JAR}")" \
    --bootstrap.servers kafka:9093 \
    --orders.topic orders \
    --output.topic processed-orders
  echo "   (submission sent; check Flink UI at http://localhost:8081)"
}

# ------------------------------------------------------------------------------
# Launch the Python data producer
# ------------------------------------------------------------------------------
start_producer() {
  echo "🐍 Starting Python data producer..."
  pip3 install -r requirements.txt
  python3 data-producer.py
}

# ------------------------------------------------------------------------------
# Main dispatch
# ------------------------------------------------------------------------------
check_prereqs

case "${1:-}" in
  kafka)
    start_kafka
    wait_for_port localhost 9092
    echo "✅ Kafka is up"
    ;;
  flink)
    down_all
    start_flink
    wait_for_port localhost 9092    # Kafka external listener
    wait_for_port localhost 8081    # Flink REST UI
    build_flink_jar
    deploy_jar
    submit_job
    start_producer
    ;;
  down)
    down_all
    echo "✅ All services stopped"
    ;;
  *)
    echo "Usage: $0 [kafka|flink|down]"
    exit 1
    ;;
esac

echo "✅ Done (mode=${1})"
