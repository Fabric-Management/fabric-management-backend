package com.fabricmanagement.flowboard.routing.domain.exception;

import com.fabricmanagement.flowboard.common.exception.FlowBoardDomainException;

public class RoutingException extends FlowBoardDomainException {
  public RoutingException(String message, String code, int status) {
    super(message, code, status);
  }
}
