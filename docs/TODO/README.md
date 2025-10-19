# 📋 TODO & Roadmap

This directory contains **production migration plans** and **technical debt** that must be addressed before production deployment.

> **Philosophy:** "Fast now, safe later" — We move fast in development but plan for production-grade security from day one.

---

## 🔴 Critical (Before Production)

| Document                                                               | Priority | Effort    | Status  |
| ---------------------------------------------------------------------- | -------- | --------- | ------- |
| [PRODUCTION_SECURITY_MIGRATION.md](./PRODUCTION_SECURITY_MIGRATION.md) | 🔴 HIGH  | 2-3 hours | 🔴 TODO |

---

## 🟡 Important (Before Scale-up)

| Item                       | Description            | When                        |
| -------------------------- | ---------------------- | --------------------------- |
| **mTLS Migration**         | Zero-trust with Istio  | After Kubernetes deployment |
| **Distributed Tracing**    | OpenTelemetry + Jaeger | When debugging becomes hard |
| **Circuit Breaker Tuning** | Fine-tune Resilience4j | After load testing          |

---

## 🟢 Nice to Have (Continuous Improvement)

| Item                      | Description                  | Benefit           |
| ------------------------- | ---------------------------- | ----------------- |
| **GraphQL Gateway**       | Unified API for frontend     | Better DX         |
| **Event Replay**          | Kafka event replay mechanism | Disaster recovery |
| **Blue/Green Deployment** | Zero-downtime deploys        | Better UX         |

---

## 📅 Review Schedule

- **Weekly:** Check critical TODOs
- **Before Staging:** Complete all 🔴 HIGH items
- **Before Production:** Complete all 🔴 + 🟡 items
- **Quarterly:** Review and update roadmap

---

**Last Updated:** October 19, 2025  
**Next Review:** Before staging deployment
