# 🔧 Troubleshooting Documentation

**Last Updated:** October 13, 2025  
**Purpose:** Troubleshooting guides and common solutions  
**Status:** ✅ Active

---

## 📚 Documentation Index

### 🆘 Quick Reference

| Document                                                           | Description                                       | When to Use              |
| ------------------------------------------------------------------ | ------------------------------------------------- | ------------------------ |
| [COMMON_ISSUES_AND_SOLUTIONS.md](./COMMON_ISSUES_AND_SOLUTIONS.md) | ⭐ **Quick fixes, debug commands, health checks** | First stop for any issue |

### 🐛 Resolved Issues (Detailed Guides)

| Issue                      | Document                                                                                      | Status      | Date Resolved |
| -------------------------- | --------------------------------------------------------------------------------------------- | ----------- | ------------- |
| **503 Timeout Onboarding** | [TENANT_ONBOARDING_503_TIMEOUT_ISSUE.md](./TENANT_ONBOARDING_503_TIMEOUT_ISSUE.md) ⭐ **NEW** | ✅ Resolved | Oct 13, 2025  |
| **Kafka Topics**           | [KAFKA_TOPIC_ISSUES.md](./KAFKA_TOPIC_ISSUES.md)                                              | ✅ Resolved | Oct 11, 2025  |
| **Bean Conflicts**         | [BEAN_CONFLICT_RESOLUTION.md](./BEAN_CONFLICT_RESOLUTION.md)                                  | ✅ Resolved | Oct 7, 2025   |
| **Flyway Checksum**        | [FLYWAY_CHECKSUM_MISMATCH.md](./FLYWAY_CHECKSUM_MISMATCH.md)                                  | ✅ Resolved | Oct 6, 2025   |

---

## 🎯 Quick Navigation

### By Problem Type

| Problem Type                | What to Check                                                                                 |
| --------------------------- | --------------------------------------------------------------------------------------------- |
| **⚡ Timeout/503 Errors**   | [TENANT_ONBOARDING_503_TIMEOUT_ISSUE.md](./TENANT_ONBOARDING_503_TIMEOUT_ISSUE.md) ⭐ **NEW** |
| **🚀 Startup Issues**       | [COMMON_ISSUES_AND_SOLUTIONS.md](./COMMON_ISSUES_AND_SOLUTIONS.md#startup-issues)             |
| **🗄️ Database Issues**      | [COMMON_ISSUES_AND_SOLUTIONS.md](./COMMON_ISSUES_AND_SOLUTIONS.md#database-issues)            |
| **📨 Kafka Issues**         | [KAFKA_TOPIC_ISSUES.md](./KAFKA_TOPIC_ISSUES.md) ⭐                                           |
| **🔗 Bean Conflicts**       | [BEAN_CONFLICT_RESOLUTION.md](./BEAN_CONFLICT_RESOLUTION.md)                                  |
| **📋 Migration Issues**     | [FLYWAY_CHECKSUM_MISMATCH.md](./FLYWAY_CHECKSUM_MISMATCH.md)                                  |
| **🔒 Internal Auth Issues** | [TENANT_ONBOARDING_503_TIMEOUT_ISSUE.md](./TENANT_ONBOARDING_503_TIMEOUT_ISSUE.md) ⭐ **NEW** |

### By Symptom

| Symptom                              | Likely Issue                | Guide                                                                        |
| ------------------------------------ | --------------------------- | ---------------------------------------------------------------------------- |
| 503 Service Unavailable              | Timeout or Circuit Breaker  | [Tenant Onboarding 503](./TENANT_ONBOARDING_503_TIMEOUT_ISSUE.md) ⭐ **NEW** |
| Service won't start                  | Check logs                  | [Common Issues - Startup](./COMMON_ISSUES_AND_SOLUTIONS.md#startup-issues)   |
| `ConflictingBeanDefinitionException` | Bean name conflict          | [Bean Conflict Resolution](./BEAN_CONFLICT_RESOLUTION.md)                    |
| `FlywayValidateException`            | Migration checksum          | [Flyway Checksum](./FLYWAY_CHECKSUM_MISMATCH.md)                             |
| Connection refused                   | Database not ready          | [Common Issues - Database](./COMMON_ISSUES_AND_SOLUTIONS.md#database-issues) |
| `LEADER_NOT_AVAILABLE`               | Kafka topics missing        | [Kafka Topic Issues](./KAFKA_TOPIC_ISSUES.md) ⭐                             |
| 401 Unauthorized (internal calls)    | Missing internal auth setup | [Tenant Onboarding 503](./TENANT_ONBOARDING_503_TIMEOUT_ISSUE.md) ⭐ **NEW** |

---

## 🛠️ Essential Commands

### Quick Health Check

```bash
# Check all services
docker compose ps

# Full health check
curl http://localhost:8081/actuator/health  # User Service
curl http://localhost:8082/actuator/health  # Contact Service
curl http://localhost:8083/actuator/health  # Company Service
```

### Quick Debug

```bash
# View logs
docker compose logs -f user-service

# Full system reset (if all else fails)
docker compose down -v && docker compose up -d
```

**📖 For complete command reference:** [COMMON_ISSUES_AND_SOLUTIONS.md](./COMMON_ISSUES_AND_SOLUTIONS.md#debugging-commands)

---

## 📊 Troubleshooting Flow

```
1. Check Service Status
   ↓
   docker compose ps
   ↓
   All healthy? → Problem might be application-level
   ↓
   Some unhealthy? → Go to step 2

2. Check Logs
   ↓
   docker compose logs <service>
   ↓
   Bean conflict? → BEAN_CONFLICT_RESOLUTION.md
   Flyway error? → FLYWAY_CHECKSUM_MISMATCH.md
   Other error? → COMMON_ISSUES_AND_SOLUTIONS.md

3. Check Dependencies
   ↓
   Database ready? → docker compose exec postgres pg_isready
   Kafka ready? → docker compose ps kafka
   Redis ready? → docker compose exec redis redis-cli ping

4. Still Not Working?
   ↓
   Full system reset → docker compose down -v && docker compose up -d
```

---

## 🆘 When to Create New Guide

Create a new troubleshooting guide when:

- ✅ Issue takes >30 minutes to debug
- ✅ Issue is likely to recur
- ✅ Root cause is non-obvious
- ✅ Solution involves multiple steps
- ✅ Affects multiple developers

**Template available in:** [COMMON_ISSUES_AND_SOLUTIONS.md](./COMMON_ISSUES_AND_SOLUTIONS.md#when-to-create-a-new-troubleshooting-guide)

---

## 🔗 Related Documentation

### Internal Links

- [Architecture](../architecture/README.md) - System design patterns
- [Development Guide](../development/README.md) - Development setup
- [Deployment Guide](../deployment/README.md) - Production deployment
- [Database Guide](../database/DATABASE_GUIDE.md) - Database schema

### External Resources

- [Spring Boot Troubleshooting](https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html)
- [Docker Troubleshooting](https://docs.docker.com/config/daemon/troubleshoot/)
- [PostgreSQL Error Codes](https://www.postgresql.org/docs/current/errcodes-appendix.html)

---

## 🤝 Contributing

Found a new issue? Solved a tricky problem?

1. **Document it** using the template in COMMON_ISSUES_AND_SOLUTIONS.md
2. **Add to index** in this README
3. **Submit PR** with clear description
4. **Help your teammates** 🎉

### Contribution Guidelines

- Use clear, descriptive titles
- Include root cause analysis
- Provide step-by-step solutions
- Add prevention tips
- Update this index

---

## 📞 Support

### Getting Help

- **Slack**: #fabric-troubleshooting (urgent issues)
- **Slack**: #fabric-dev (general questions)
- **Office Hours**: Daily standup (9:00 AM)

### Escalation Path

1. Check this documentation
2. Ask in #fabric-dev
3. Create GitHub issue with `bug` label
4. Tag @team-lead for urgent issues

---

**Maintained By:** Development Team  
**Last Updated:** 2025-10-13  
**Version:** 2.1 (Added Tenant Onboarding 503 Timeout Issue)  
**Status:** ✅ Active - Updated with new issues as they occur
