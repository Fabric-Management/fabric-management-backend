package com.fabricmanagement.common.infrastructure.security;

import com.fabricmanagement.common.infrastructure.web.exception.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Custom AuthenticationEntryPoint to return 401 Unauthorized instead of default 403 Forbidden when
 * an unauthenticated user attempts to access a secured endpoint.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException, ServletException {

    log.warn(
        "Unauthorized access attempt: {} — {}",
        request.getRequestURI(),
        authException.getMessage());

    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);

    ApiError apiError =
        ApiError.of(
            HttpStatus.UNAUTHORIZED.value(),
            "Unauthorized",
            "UNAUTHORIZED",
            "Authentication is required to access this resource",
            request.getRequestURI());

    objectMapper.writeValue(response.getOutputStream(), apiError);
  }
}
