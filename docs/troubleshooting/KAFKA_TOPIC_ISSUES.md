# Kafka Topic Issues - Troubleshooting Guide

**Last Updated:** 2025-10-11  
**Issue:** `LEADER_NOT_AVAILABLE` errors  
**Status:** ✅ Solution Documented

---

## 🚨 Problem: LEADER_NOT_AVAILABLE

### Symptoms

```
WARN  o.apache.kafka.clients.NetworkClient - [Consumer]
Error while fetching metadata with correlation id X :
{company-events=LEADER_NOT_AVAILABLE}
{contact.created=LEADER_NOT_AVAILABLE}
{contact.verified=LEADER_NOT_AVAILABLE}
{contact.deleted=LEADER_NOT_AVAILABLE}
```

### Root Cause

Kafka topics are not created or leader election has not completed.

**Why This Happens:**

1. Topics don't exist yet (first run)
2. Kafka auto-create is disabled
3. Service started before Kafka was ready
4. Kafka broker not fully initialized

---

## ✅ Solution: Create Topics Manually

### Quick Fix (Run This)

```bash
# Navigate to project root
cd /Users/user/Coding/fabric-management/fabric-management-backend

# Make script executable
chmod +x scripts/kafka-init-topics.sh

# Run topic initialization script
./scripts/kafka-init-topics.sh
```

**What it does:**

- ✅ Checks Kafka is running
- ✅ Creates all required topics
- ✅ Lists created topics
- ✅ Shows topic details

**Expected output:**

```
🚀 Creating Kafka topics...
⏳ Waiting for Kafka to be ready...

📝 Creating topics...
  → company-events
  → contact.created
  → contact.verified
  → contact.deleted
  → user-events
  → policy.audit

✅ Kafka topics created successfully!

📋 All topics:
company-events
contact.created
contact.deleted
contact.verified
policy.audit
user-events
```

---

## 🔍 Verification

### 1. Check Topics Created

```bash
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092
```

**Expected:** 6 topics listed

### 2. Check Topic Details

```bash
docker exec kafka kafka-topics --describe --bootstrap-server localhost:9092 --topic company-events
```

**Expected:**

```
Topic: company-events
PartitionCount: 3
ReplicationFactor: 1
Leader: 1
```

### 3. Restart Services

```bash
# If running with Docker
docker-compose restart user-service company-service contact-service

# If running with Maven
# Just restart the Maven processes (Ctrl+C and mvn spring-boot:run again)
```

### 4. Check Logs (Should be clean now)

```bash
docker-compose logs user-service | grep -i "LEADER_NOT_AVAILABLE"
# Should return nothing!
```

---

## 🔧 Alternative Solutions

### Solution 2: Enable Kafka Auto-Create

**Edit Kafka configuration in docker-compose.yml:**

```yaml
kafka:
  environment:
    # ... existing config
    KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true" # Add this
```

**Then restart Kafka:**

```bash
docker-compose restart kafka
```

**Pros:** Topics auto-created when first message published  
**Cons:** No control over partitions/replication

---

### Solution 3: Manual Topic Creation

```bash
# Create each topic individually
docker exec kafka kafka-topics --create --if-not-exists \
  --topic company-events \
  --bootstrap-server localhost:9092 \
  --partitions 3 \
  --replication-factor 1

# Repeat for other topics...
```

---

## 🚫 Prevention

### Startup Order (Recommended)

```bash
# 1. Start Kafka first
docker-compose up -d kafka zookeeper

# 2. Wait for Kafka to be ready (30 seconds)
sleep 30

# 3. Create topics
./scripts/kafka-init-topics.sh

# 4. Start services
docker-compose up -d user-service company-service contact-service
# OR
mvn spring-boot:run  # In each service
```

### Docker Compose Init Container (Future)

Add to docker-compose.yml:

```yaml
kafka-init:
  image: confluentinc/cp-kafka:latest
  depends_on:
    - kafka
  entrypoint: ["/bin/sh", "/scripts/kafka-init-topics.sh"]
  volumes:
    - ./scripts:/scripts
```

---

## 📊 Topic Configuration

### Required Topics

| Topic              | Partitions | Replication | Retention | Purpose              |
| ------------------ | ---------- | ----------- | --------- | -------------------- |
| `company-events`   | 3          | 1           | 7 days    | Company CRUD events  |
| `contact.created`  | 3          | 1           | 7 days    | Contact creation     |
| `contact.verified` | 3          | 1           | 7 days    | Contact verification |
| `contact.deleted`  | 3          | 1           | 7 days    | Contact deletion     |
| `user-events`      | 3          | 1           | 7 days    | User CRUD events     |
| `policy.audit`     | 3          | 1           | 7 days    | Policy audit logs    |

**Total:** 6 topics

---

## 🐛 Common Errors

### Error: "Connection refused"

```
[AdminClient] Connection to node -1 could not be established.
Broker may not be available.
```

**Cause:** Kafka not running or not ready  
**Fix:** Start Kafka and wait 30 seconds

```bash
docker-compose up -d kafka zookeeper
sleep 30
./scripts/kafka-init-topics.sh
```

---

### Error: "Container not found"

```
Error: No such container: kafka
```

**Cause:** Kafka container name different  
**Fix:** Check container name

```bash
docker ps | grep kafka
# Use actual container name in commands
```

---

### Error: "Topic already exists"

```
Topic 'company-events' already exists
```

**Cause:** Topic already created  
**Fix:** This is OK! Script uses `--if-not-exists` flag

No action needed. ✅

---

## 🧪 Testing After Fix

### 1. Verify Topics Exist

```bash
./scripts/kafka-init-topics.sh
# Should show all 6 topics
```

### 2. Check Service Logs

```bash
docker-compose logs user-service | tail -50
# Should NOT show LEADER_NOT_AVAILABLE
```

### 3. Test Kafka Event

```bash
# Create a company (triggers company-events)
curl -X POST http://localhost:8083/api/v1/companies \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{...company data...}'

# Check Kafka messages
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic company-events \
  --from-beginning \
  --max-messages 1
```

---

## 📝 Best Practices

### 1. Always Create Topics First

```bash
# Bad workflow
docker-compose up -d  # Everything starts
# Topics missing, errors appear

# Good workflow
docker-compose up -d kafka zookeeper
sleep 30
./scripts/kafka-init-topics.sh
docker-compose up -d user-service company-service contact-service
```

### 2. Monitor Topic Health

```bash
# Check topic health periodically
docker exec kafka kafka-topics --describe --bootstrap-server localhost:9092

# Check consumer lag
docker exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --describe --all-groups
```

### 3. Development vs Production

**Development:**

- Low partition count (3) - OK
- Low replication (1) - OK
- 7-day retention - OK

**Production:**

- Higher partitions (6-9) - Better throughput
- Higher replication (3) - Fault tolerance
- 30-day retention - Compliance

---

## 🔗 Related Issues

- [Common Issues](./COMMON_ISSUES_AND_SOLUTIONS.md) - General troubleshooting
- [Docker Optimization](../reports/DOCKER_OPTIMIZATION_AND_INTEGRATION_GUIDE.md) - Docker setup

---

## ✅ Checklist

After running fix:

- [ ] Kafka container running (`docker ps | grep kafka`)
- [ ] Topics created (`docker exec kafka kafka-topics --list`)
- [ ] 6 topics visible (company-events, contact.\*, user-events, policy.audit)
- [ ] Services restarted
- [ ] No LEADER_NOT_AVAILABLE in logs
- [ ] Test event published successfully

---

**Last Updated:** 2025-10-11  
**Issue Type:** Configuration  
**Severity:** Medium (blocks event-driven features)  
**Fix Time:** 2 minutes
