# 🌐 WEB MODULE PROTOCOL

**Version:** 1.0  
**Last Updated:** 2025-01-27  
**Module:** `common/infrastructure/web`  
**Dependencies:** None

---

## 🎯 MODULE PURPOSE

Web utilities for REST API responses and exception handling.

---

## 📂 STRUCTURE

```
web/
├─ ApiResponse.java
├─ PagedResponse.java
├─ GlobalExceptionHandler.java
└─ ProblemDetails.java
```

---

## 💡 EXAMPLE

```java
@Data
@Builder
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private String message;
    private Instant timestamp;

    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
            .success(true)
            .data(data)
            .message(message)
            .timestamp(Instant.now())
            .build();
    }
}
```

---

**Last Updated:** 2025-01-27
