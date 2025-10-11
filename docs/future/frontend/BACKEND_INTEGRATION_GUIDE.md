# 🔗 Backend Integration Guide

**Last Updated:** 2025-10-11  
**Status:** 🔥 Active Development  
**Purpose:** Patterns and best practices for frontend-backend integration

---

## 📋 Table of Contents

- [Overview](#overview)
- [API Client Setup](#api-client-setup)
- [Pagination Implementation](#pagination-implementation)
- [Infinite Scroll Pattern](#infinite-scroll-pattern)
- [Error Handling](#error-handling)
- [Authentication Flow](#authentication-flow)
- [Type Safety](#type-safety)
- [Performance Optimization](#performance-optimization)

---

## 🎯 Overview

This guide provides production-ready patterns for integrating with Fabric Management backend services. All examples use TypeScript with Next.js 14+ (App Router).

**Backend Architecture:**

- Microservices (Spring Boot)
- API Gateway (Port 8080)
- JWT Authentication
- RESTful APIs with standard response format

---

## 🔧 API Client Setup

### Base Configuration

```typescript
// lib/api/client.ts
import axios, { AxiosInstance } from "axios";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export const apiClient: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
  headers: {
    "Content-Type": "application/json",
  },
});

// Request interceptor - Add JWT token
apiClient.interceptors.request.use(
  config => {
    const token = localStorage.getItem("accessToken");
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  error => Promise.reject(error)
);

// Response interceptor - Handle errors
apiClient.interceptors.response.use(
  response => response.data, // Extract data automatically
  error => {
    if (error.response?.status === 401) {
      // Redirect to login
      window.location.href = "/login";
    }
    return Promise.reject(error);
  }
);
```

---

## 📄 Pagination Implementation

### Backend Pagination Response Format

```typescript
// types/pagination.ts
export interface PagedResponse<T> {
  content: T[];
  page: number; // Current page (0-indexed)
  size: number; // Page size
  totalElements: number; // Total items
  totalPages: number; // Total pages
  first: boolean; // Is first page?
  last: boolean; // Is last page?
  success: boolean;
  message?: string;
  timestamp: string;
}

export interface PaginationParams {
  page?: number; // Default: 0
  size?: number; // Default: 20 (max: 100)
  sortBy?: string; // Default: 'name'
  sortDirection?: "ASC" | "DESC"; // Default: 'ASC'
}
```

### ✅ Company Service - Paginated List

**Backend Endpoint:**

```
GET /api/v1/companies/paginated?page=0&size=20&sortBy=name&sortDirection=ASC
```

**Frontend Implementation:**

```typescript
// lib/api/company.service.ts
import { apiClient } from "./client";
import type { PagedResponse, PaginationParams } from "@/types/pagination";
import type { Company } from "@/types/company";

export class CompanyService {
  /**
   * Get paginated company list
   *
   * @param params - Pagination parameters
   * @returns Paginated company list
   */
  static async getCompaniesPaginated(
    params: PaginationParams = {}
  ): Promise<PagedResponse<Company>> {
    const {
      page = 0,
      size = 20,
      sortBy = "name",
      sortDirection = "ASC",
    } = params;

    const response = await apiClient.get<PagedResponse<Company>>(
      "/api/v1/companies/paginated",
      {
        params: { page, size, sortBy, sortDirection },
      }
    );

    return response;
  }

  /**
   * Search companies with pagination
   */
  static async searchCompaniesPaginated(
    searchTerm: string,
    params: PaginationParams = {}
  ): Promise<PagedResponse<Company>> {
    const {
      page = 0,
      size = 20,
      sortBy = "name",
      sortDirection = "ASC",
    } = params;

    return apiClient.get("/api/v1/companies/search/paginated", {
      params: { name: searchTerm, page, size, sortBy, sortDirection },
    });
  }

  /**
   * Get companies by status with pagination
   */
  static async getCompaniesByStatusPaginated(
    status: string,
    params: PaginationParams = {}
  ): Promise<PagedResponse<Company>> {
    const {
      page = 0,
      size = 20,
      sortBy = "name",
      sortDirection = "ASC",
    } = params;

    return apiClient.get(`/api/v1/companies/status/${status}/paginated`, {
      params: { page, size, sortBy, sortDirection },
    });
  }
}
```

### React Component with Pagination

```typescript
// app/companies/page.tsx
"use client";

import { useState, useEffect } from "react";
import { CompanyService } from "@/lib/api/company.service";
import type { PagedResponse } from "@/types/pagination";
import type { Company } from "@/types/company";

export default function CompaniesPage() {
  const [data, setData] = useState<PagedResponse<Company> | null>(null);
  const [loading, setLoading] = useState(false);
  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);

  useEffect(() => {
    loadCompanies();
  }, [currentPage, pageSize]);

  const loadCompanies = async () => {
    setLoading(true);
    try {
      const response = await CompanyService.getCompaniesPaginated({
        page: currentPage,
        size: pageSize,
        sortBy: "name",
        sortDirection: "ASC",
      });
      setData(response);
    } catch (error) {
      console.error("Failed to load companies:", error);
    } finally {
      setLoading(false);
    }
  };

  if (loading) return <div>Loading...</div>;
  if (!data) return <div>No data</div>;

  return (
    <div>
      {/* Company List */}
      <div className="grid gap-4">
        {data.content.map(company => (
          <CompanyCard key={company.id} company={company} />
        ))}
      </div>

      {/* Pagination Controls */}
      <div className="flex items-center justify-between mt-6">
        <div className="text-sm text-gray-700">
          Showing {data.page * data.size + 1} to{" "}
          {Math.min((data.page + 1) * data.size, data.totalElements)} of{" "}
          {data.totalElements} results
        </div>

        <div className="flex gap-2">
          <button
            onClick={() => setCurrentPage(p => p - 1)}
            disabled={data.first}
            className="px-4 py-2 bg-blue-500 text-white rounded disabled:opacity-50">
            Previous
          </button>

          <span className="px-4 py-2">
            Page {data.page + 1} of {data.totalPages}
          </span>

          <button
            onClick={() => setCurrentPage(p => p + 1)}
            disabled={data.last}
            className="px-4 py-2 bg-blue-500 text-white rounded disabled:opacity-50">
            Next
          </button>
        </div>
      </div>
    </div>
  );
}
```

---

## ∞ Infinite Scroll Pattern

### Using `hasNext` and Pagination

```typescript
// hooks/useInfiniteCompanies.ts
import { useState, useEffect, useRef, useCallback } from "react";
import { CompanyService } from "@/lib/api/company.service";
import type { Company } from "@/types/company";

export function useInfiniteCompanies(pageSize = 20) {
  const [companies, setCompanies] = useState<Company[]>([]);
  const [loading, setLoading] = useState(false);
  const [hasMore, setHasMore] = useState(true);
  const [page, setPage] = useState(0);

  const observer = useRef<IntersectionObserver>();

  const lastElementRef = useCallback(
    (node: HTMLElement | null) => {
      if (loading) return;
      if (observer.current) observer.current.disconnect();

      observer.current = new IntersectionObserver(entries => {
        if (entries[0].isIntersecting && hasMore) {
          setPage(prevPage => prevPage + 1);
        }
      });

      if (node) observer.current.observe(node);
    },
    [loading, hasMore]
  );

  useEffect(() => {
    loadMoreCompanies();
  }, [page]);

  const loadMoreCompanies = async () => {
    setLoading(true);
    try {
      const response = await CompanyService.getCompaniesPaginated({
        page,
        size: pageSize,
        sortBy: "createdAt",
        sortDirection: "DESC",
      });

      setCompanies(prev => [...prev, ...response.content]);

      // ✅ Use PagedResponse.last to determine if there's more data
      setHasMore(!response.last);
    } catch (error) {
      console.error("Failed to load companies:", error);
    } finally {
      setLoading(false);
    }
  };

  return { companies, loading, hasMore, lastElementRef };
}
```

### Usage in Component

```typescript
// app/companies/infinite-scroll-page.tsx
"use client";

import { useInfiniteCompanies } from "@/hooks/useInfiniteCompanies";

export default function InfiniteScrollPage() {
  const { companies, loading, hasMore, lastElementRef } =
    useInfiniteCompanies(20);

  return (
    <div className="space-y-4">
      {companies.map((company, index) => {
        // Attach ref to last element
        if (companies.length === index + 1) {
          return (
            <div ref={lastElementRef} key={company.id}>
              <CompanyCard company={company} />
            </div>
          );
        }
        return <CompanyCard key={company.id} company={company} />;
      })}

      {loading && <div>Loading more...</div>}
      {!hasMore && <div>No more companies</div>}
    </div>
  );
}
```

**Key Points:**

- ✅ Use `response.last` to know when to stop loading
- ✅ Use `response.totalElements` for total count
- ✅ Append new items to existing array
- ✅ Increment page number on scroll

---

## 🛡️ Error Handling

### Backend Error Response Format

```typescript
// types/error.ts
export interface ErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  details?: Record<string, string>; // Field-specific errors
}
```

### Error Handler Utility

```typescript
// lib/utils/error-handler.ts
import { AxiosError } from "axios";
import type { ErrorResponse } from "@/types/error";

export class ApiError extends Error {
  status: number;
  code: string;
  details?: Record<string, string>;

  constructor(error: AxiosError<ErrorResponse>) {
    const data = error.response?.data;
    super(data?.message || "An error occurred");

    this.status = error.response?.status || 500;
    this.code = data?.error || "UNKNOWN_ERROR";
    this.details = data?.details;
  }

  isValidationError(): boolean {
    return this.status === 400;
  }

  isUnauthorized(): boolean {
    return this.status === 401;
  }

  isForbidden(): boolean {
    return this.status === 403;
  }

  isNotFound(): boolean {
    return this.status === 404;
  }
}

export function handleApiError(error: unknown): ApiError {
  if (error instanceof AxiosError) {
    return new ApiError(error);
  }
  return new ApiError({
    response: {
      status: 500,
      data: {
        timestamp: new Date().toISOString(),
        status: 500,
        error: "UNKNOWN_ERROR",
        message: "An unexpected error occurred",
      },
    },
  } as AxiosError<ErrorResponse>);
}
```

### Error Handling in Components

```typescript
// app/companies/[id]/page.tsx
"use client";

import { useState, useEffect } from "react";
import { CompanyService } from "@/lib/api/company.service";
import { handleApiError } from "@/lib/utils/error-handler";
import { useToast } from "@/hooks/use-toast";

export default function CompanyDetailPage({
  params,
}: {
  params: { id: string };
}) {
  const [company, setCompany] = useState(null);
  const { toast } = useToast();

  useEffect(() => {
    loadCompany();
  }, [params.id]);

  const loadCompany = async () => {
    try {
      const data = await CompanyService.getCompanyById(params.id);
      setCompany(data);
    } catch (error) {
      const apiError = handleApiError(error);

      // Show user-friendly error message
      if (apiError.isNotFound()) {
        toast({
          variant: "destructive",
          title: "Company Not Found",
          description:
            "The requested company does not exist or has been deleted.",
        });
      } else if (apiError.isUnauthorized()) {
        // Redirect to login (handled by interceptor)
      } else {
        toast({
          variant: "destructive",
          title: "Error",
          description: apiError.message,
        });
      }
    }
  };

  // ...rest of component
}
```

---

## 🔐 Authentication Flow

### JWT Token Management

```typescript
// lib/auth/token-manager.ts
export class TokenManager {
  private static ACCESS_TOKEN_KEY = "accessToken";
  private static REFRESH_TOKEN_KEY = "refreshToken";

  static setTokens(accessToken: string, refreshToken?: string) {
    localStorage.setItem(this.ACCESS_TOKEN_KEY, accessToken);
    if (refreshToken) {
      localStorage.setItem(this.REFRESH_TOKEN_KEY, refreshToken);
    }
  }

  static getAccessToken(): string | null {
    return localStorage.getItem(this.ACCESS_TOKEN_KEY);
  }

  static clearTokens() {
    localStorage.removeItem(this.ACCESS_TOKEN_KEY);
    localStorage.removeItem(this.REFRESH_TOKEN_KEY);
  }

  static isAuthenticated(): boolean {
    return !!this.getAccessToken();
  }
}
```

### Auth Service

```typescript
// lib/api/auth.service.ts
import { apiClient } from "./client";
import { TokenManager } from "@/lib/auth/token-manager";

export interface LoginRequest {
  contactValue: string; // email or phone
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  userId: string;
  role: string;
}

export class AuthService {
  static async login(credentials: LoginRequest): Promise<LoginResponse> {
    const response = await apiClient.post<LoginResponse>(
      "/api/v1/users/auth/login",
      credentials
    );

    // Store tokens
    TokenManager.setTokens(response.accessToken, response.refreshToken);

    return response;
  }

  static logout() {
    TokenManager.clearTokens();
    window.location.href = "/login";
  }
}
```

---

## 🎨 Type Safety

### Company Types

```typescript
// types/company.ts
export interface Company {
  id: string; // UUID
  name: string;
  legalName: string;
  taxId: string;
  registrationNumber?: string;
  type: CompanyType;
  industry: Industry;
  status: CompanyStatus;
  description?: string;
  website?: string;
  logoUrl?: string;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export enum CompanyType {
  CORPORATION = "CORPORATION",
  LLC = "LLC",
  PARTNERSHIP = "PARTNERSHIP",
  SOLE_PROPRIETORSHIP = "SOLE_PROPRIETORSHIP",
  NON_PROFIT = "NON_PROFIT",
}

export enum CompanyStatus {
  ACTIVE = "ACTIVE",
  INACTIVE = "INACTIVE",
  PENDING = "PENDING",
  SUSPENDED = "SUSPENDED",
  DELETED = "DELETED",
}

export enum Industry {
  MANUFACTURING = "MANUFACTURING",
  RETAIL = "RETAIL",
  TECHNOLOGY = "TECHNOLOGY",
  HEALTHCARE = "HEALTHCARE",
  FINANCE = "FINANCE",
  EDUCATION = "EDUCATION",
  OTHER = "OTHER",
}
```

---

## ⚡ Performance Optimization

### 1. Debounced Search

```typescript
// hooks/useDebouncedSearch.ts
import { useState, useEffect } from "react";

export function useDebouncedValue<T>(value: T, delay: number = 300): T {
  const [debouncedValue, setDebouncedValue] = useState<T>(value);

  useEffect(() => {
    const handler = setTimeout(() => {
      setDebouncedValue(value);
    }, delay);

    return () => {
      clearTimeout(handler);
    };
  }, [value, delay]);

  return debouncedValue;
}

// Usage
const [searchTerm, setSearchTerm] = useState("");
const debouncedSearch = useDebouncedValue(searchTerm, 500);

useEffect(() => {
  if (debouncedSearch.length >= 2) {
    searchCompanies(debouncedSearch);
  }
}, [debouncedSearch]);
```

### 2. SWR for Data Fetching (Recommended)

```typescript
// hooks/useCompanies.ts
import useSWR from "swr";
import { CompanyService } from "@/lib/api/company.service";
import type { PaginationParams } from "@/types/pagination";

export function useCompanies(params: PaginationParams) {
  const key = ["companies", params];

  const { data, error, isLoading, mutate } = useSWR(
    key,
    () => CompanyService.getCompaniesPaginated(params),
    {
      revalidateOnFocus: false,
      revalidateOnReconnect: true,
      dedupingInterval: 5000, // 5 seconds
    }
  );

  return {
    companies: data?.content || [],
    pagination: data,
    isLoading,
    isError: !!error,
    mutate, // For manual revalidation
  };
}

// Usage
const { companies, pagination, isLoading } = useCompanies({
  page: 0,
  size: 20,
});
```

---

## 📝 Best Practices Checklist

### API Integration

- [ ] Use TypeScript for all API calls
- [ ] Define interfaces for all request/response types
- [ ] Handle errors gracefully with user-friendly messages
- [ ] Show loading states during API calls
- [ ] Implement request/response interceptors for common logic

### Pagination

- [ ] Use `page`, `size`, `sortBy`, `sortDirection` parameters
- [ ] Display total count and page info to users
- [ ] Disable pagination buttons appropriately (first/last page)
- [ ] Consider infinite scroll for mobile/long lists
- [ ] Cache pagination results when possible

### Error Handling

- [ ] Show specific error messages (not "Error occurred")
- [ ] Handle 401 (redirect to login)
- [ ] Handle 403 (show permission denied)
- [ ] Handle 404 (show not found message)
- [ ] Log errors for debugging

### Performance

- [ ] Debounce search inputs (300-500ms)
- [ ] Use SWR or React Query for caching
- [ ] Implement virtual scrolling for large lists
- [ ] Lazy load images
- [ ] Minimize re-renders

---

## 🔗 Related Documentation

- [Frontend Technology Stack](./FRONTEND_TECHNOLOGY_STACK.md) - Tech choices and setup
- [Company Service Analysis](../../reports/COMPANY_SERVICE_ENDPOINT_ANALYSIS.md) - Backend API details
- [Microservices API Standards](../../development/MICROSERVICES_API_STANDARDS.md) - API patterns
- [Data Types Standards](../../development/DATA_TYPES_STANDARDS.md) - UUID usage

---

## 📞 Support

**Questions?** #fabric-frontend on Slack  
**Backend Issues?** #fabric-backend on Slack  
**API Documentation:** http://localhost:8080/swagger-ui.html

---

**Last Updated:** 2025-10-11  
**Version:** 1.0  
**Status:** ✅ Ready for Development
