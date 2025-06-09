#!/usr/bin/env python3
import json
import time
from datetime import datetime, timedelta
from dateutil import parser
from kafka import KafkaProducer
import pytz
from tzlocal import get_localzone  # Import get_localzone to fetch the local timezone

# Configuration
KAFKA_BROKER = 'localhost:9092'
TOPIC = 'orders'
DATA_FILE = 'orders.json'

producer = KafkaProducer(
    bootstrap_servers=[KAFKA_BROKER],
    value_serializer=lambda v: json.dumps(v).encode('utf-8'),
    acks='all',
    retries=3
)

def load_orders(file_path):
    """Load orders.json, parse each timestamp into a datetime, store in '_ts'."""
    orders = []
    with open(file_path, 'r') as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            o = json.loads(line)
            o['_ts'] = parser.isoparse(o['timestamp'])
            orders.append(o)
    return orders

if __name__ == '__main__':
    orders = load_orders(DATA_FILE)
    if not orders:
        print(f"No orders found in {DATA_FILE}. Exiting.")
        exit(1)

    # Compute the earliest original timestamp
    t_min = min(o['_ts'] for o in orders)

    # Use the machine's local timezone
    local_tz = get_localzone()  # Automatically fetch the local timezone

    # Determine the start of the next full minute (second=0) in local timezone
    now = datetime.now(local_tz)
    t0 = now.replace(second=0, microsecond=0) + timedelta(minutes=1)
    wait_secs = (t0 - now).total_seconds()
    print(f"⏱  Waiting until {t0.isoformat()} to start production…")
    if wait_secs > 0:
        time.sleep(wait_secs)

    print("▶️  Starting real-time production…")
    for o in orders:
        # Calculate the relative delay from t_min
        delta: timedelta = o['_ts'] - t_min
        send_time = t0 + delta

        # Sleep until the correct real-time moment
        now = datetime.now(local_tz)
        sleep_secs = (send_time - now).total_seconds()
        if sleep_secs > 0:
            time.sleep(sleep_secs)

        # Overwrite timestamp to actual send time in local timezone
        o['timestamp'] = datetime.now(local_tz).isoformat()
        del o['_ts']

        producer.send(TOPIC, value=o)
        print(f"→ Sent order_id={o['order_id']} @ {o['timestamp']}")

    producer.flush()
    producer.close()
    print("✅ All orders sent. Exiting.")
