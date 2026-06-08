package com.fabricmanagement.common.infrastructure.security;

import com.fabricmanagement.common.infrastructure.web.exception.ApiProblemDetail;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
    response.setContentType("application/problem+json");

    ApiProblemDetail problemDetail =
        ApiProblemDetail.forStatusAndDetail(
            HttpStatus.UNAUTHORIZED, "Authentication is required to access this resource");
    problemDetail.setTitle("Unauthorized");
    problemDetail.setCode("UNAUTHORIZED");
    problemDetail.setInstance(URI.create(request.getRequestURI()));

    objectMapper.writeValue(response.getOutputStream(), problemDetail);
  }
}
