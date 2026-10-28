package com.fabricmanagement.flowboard.collection.api.controller;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.web.*;
import com.fabricmanagement.flowboard.collection.app.CollectionFollowUpService;
import com.fabricmanagement.flowboard.collection.dto.CollectionFollowUpDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/flowboard/collection-follow-ups")
@RequiredArgsConstructor
@Tag(
    name = "Collection follow-up",
    description = "Governed collection work, appointments and contact evidence")
@io.swagger.v3.oas.annotations.responses.ApiResponse(
    responseCode = "409",
    description = "Source, case, task, appointment or operation replay conflict",
    content =
        @io.swagger.v3.oas.annotations.media.Content(
            mediaType = "application/problem+json",
            schema =
                @io.swagger.v3.oas.annotations.media.Schema(
                    implementation =
                        com.fabricmanagement.common.infrastructure.web.exception.ApiProblemDetail
                            .class)))
public class CollectionFollowUpController {
  private final CollectionFollowUpService service;
  private final com.fabricmanagement.flowboard.task.app.adapter.CollectionFollowUpActionAdapter
      actions;

  @GetMapping("/{id}")
  @PreAuthorize(
      "@auth.can(authentication, 'flowboard', 'read') and @auth.can(authentication, 'finance',"
          + " 'read')")
  @Operation(
      operationId = "getCollectionFollowUp",
      summary = "Read a collection work episode and backend actions")
  public ApiResponse<Detail> detail(
      org.springframework.security.core.Authentication auth, @PathVariable UUID id) {
    return ApiResponse.success(service.detail(id, actor(auth)));
  }

  @GetMapping("/{id}/contacts")
  @PreAuthorize(
      "@auth.can(authentication, 'flowboard', 'read') and @auth.can(authentication, 'finance',"
          + " 'read')")
  @Operation(
      operationId = "getCollectionContacts",
      summary = "Read paged contact and promise evidence")
  public ApiResponse<PagedResponse<Contact>> contacts(
      org.springframework.security.core.Authentication auth,
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(service.contacts(id, actor(auth), page, size));
  }

  @PostMapping("/{id}/contacts")
  @PreAuthorize(
      "@auth.can(authentication, 'flowboard', 'write') and @auth.can(authentication, 'finance',"
          + " 'read')")
  @Operation(
      operationId = "recordCollectionContact",
      summary = "Record contact or promise without posting money")
  public ApiResponse<Result> contact(
      org.springframework.security.core.Authentication auth,
      @PathVariable UUID id,
      @Valid @RequestBody ContactRequest request) {
    return ApiResponse.success(actions.execute(id, actor(auth), request));
  }

  @PostMapping("/{id}/responsible")
  @PreAuthorize(
      "@auth.can(authentication, 'flowboard', 'manage-routing') and @auth.can(authentication,"
          + " 'finance', 'read')")
  @Operation(
      operationId = "assignCollectionResponsible",
      summary = "Appoint an eligible collection pool member")
  public ApiResponse<Result> assign(
      org.springframework.security.core.Authentication auth,
      @PathVariable UUID id,
      @Valid @RequestBody AssignRequest request) {
    return ApiResponse.success(actions.execute(id, actor(auth), request));
  }

  private static UUID actor(org.springframework.security.core.Authentication auth) {
    if (auth != null
        && auth.getPrincipal() instanceof AuthenticatedUserContext user
        && user.userId() != null) return user.userId();
    throw new org.springframework.security.access.AccessDeniedException(
        "A signed-in collection user is required");
  }
}
