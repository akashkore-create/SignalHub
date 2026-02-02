from kafka import KafkaProducer
import json
import uuid

# Configuration
KAFKA_BROKER = 'pkc-921jm.us-east-2.aws.confluent.cloud:9092'
SIMPLE_USERNAME = 'WB7S2V3RBYRNYEPW'
SIMPLE_PASSWORD = 'cfltpXczf/lgVg0E8sEmJvuUWSbLF4faXEe362WazAfv1BtYohUOeIog88yqSGTw'
TOPIC = 'notification-requests'
TARGET_USER = 'USR1750868955230'

def send_notification():
    try:
        producer = KafkaProducer(
            bootstrap_servers=[KAFKA_BROKER],
            security_protocol='SASL_SSL',
            sasl_mechanism='PLAIN',
            sasl_plain_username=SIMPLE_USERNAME,
            sasl_plain_password=SIMPLE_PASSWORD,
            value_serializer=lambda v: json.dumps(v).encode('utf-8')
        )

        event = {
            "eventId": str(uuid.uuid4()),
            "userId": TARGET_USER,
            "recipient": TARGET_USER,
            "type": "PUSH", # Default type
            "templateName": "custom_push",
            "params": {
                "title": "Test Push & Email",
                "body": "This is a verification test for simultaneous push and email dispatch."
            },
            "language": "en",
            "senderConfig": None,
            "triggerId": "manual_test",
            "metadata": {
                "source": "manual_script"
            },
            "sendPush": True,
            "sendEmail": True
        }

        print(f"Sending event to {TOPIC}: {json.dumps(event, indent=2)}")
        future = producer.send(TOPIC, event)
        record_metadata = future.get(timeout=10)
        print(f"Message sent successfully to partition {record_metadata.partition} at offset {record_metadata.offset}")

    except Exception as e:
        print(f"Failed to send message: {e}")
    finally:
        if 'producer' in locals():
            producer.close()

if __name__ == "__main__":
    send_notification()
