package com.fabricmanagement.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.bootstrap.SalesQuoteDemoSeeder;
import com.fabricmanagement.sales.ownership.app.CustomerOwnershipResolvedListener;
import com.fabricmanagement.sales.quote.app.QuoteService;
import com.fabricmanagement.sales.quote.dto.AddQuoteLineRequest;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class SalesEndpointAuthorizationArchTest {

  private enum Category {
    TRANSACTIONAL_READ,
    CREATE,
    MUTATION,
    REFERENCE_READ,
    CATALOGUE_MANAGEMENT,
    OWNERSHIP_OPERATION,
    PUBLIC_TOKEN
  }

  @Test
  void everyMappedSalesMethodHasExactlyOneExplicitCategory() throws Exception {
    Map<String, Category> expected = expectedClassifications();

    assertThat(mappedSalesMethods())
        .hasSize(134)
        .containsExactlyInAnyOrderElementsOf(expected.keySet());
    assertThat(expected).hasSize(134);
  }

  @Test
  void exceptionalOwnershipReadsKeepTheirLockedActionPairs() throws Exception {
    assertThat(preAuthorize("OwnershipTriageController", "list"))
        .isEqualTo("@auth.can(authentication, 'sales', 'assign-owner')");
    assertThat(preAuthorize("CustomerAccountTeamController", "listCandidates"))
        .isEqualTo("@auth.can(authentication, 'sales', 'write')");
  }

  @Test
  void unscopedQuoteEntriesAreNotPublicAndTheDemoBypassIsNamed() throws Exception {
    assertThat(
            QuoteService.class.getDeclaredMethod(
                "addQuoteLine", UUID.class, AddQuoteLineRequest.class))
        .matches(method -> !Modifier.isPublic(method.getModifiers()));
    assertThat(
            QuoteService.class.getDeclaredMethod(
                "addQuoteLineForDemoSeed", UUID.class, AddQuoteLineRequest.class))
        .matches(method -> Modifier.isPublic(method.getModifiers()));
  }

  @Test
  void namedSystemQuoteEntriesHaveExplicitProductionCallerAllowlists() {
    var productionClasses =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.fabricmanagement");

    noClasses()
        .that()
        .doNotHaveFullyQualifiedName(SalesQuoteDemoSeeder.class.getName())
        .should()
        .callMethod(
            QuoteService.class, "addQuoteLineForDemoSeed", UUID.class, AddQuoteLineRequest.class)
        .check(productionClasses);
    noClasses()
        .that()
        .doNotHaveFullyQualifiedName(CustomerOwnershipResolvedListener.class.getName())
        .should()
        .callMethod(
            QuoteService.class,
            "backfillUnassignedActionableQuotes",
            UUID.class,
            UUID.class,
            UUID.class)
        .check(productionClasses);
  }

  private Set<String> mappedSalesMethods() throws Exception {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
    Set<String> methods = new java.util.LinkedHashSet<>();
    for (var component : scanner.findCandidateComponents("com.fabricmanagement.sales")) {
      Class<?> controller = Class.forName(component.getBeanClassName());
      for (Method method : controller.getMethods()) {
        if (isMapped(method)) {
          methods.add(key(controller, method));
        }
      }
    }
    return Set.copyOf(methods);
  }

  private String preAuthorize(String controllerName, String methodName) throws Exception {
    Method method =
        java.util.Arrays.stream(findController(controllerName).getMethods())
            .filter(candidate -> candidate.getName().equals(methodName))
            .findFirst()
            .orElseThrow();
    PreAuthorize annotation =
        AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
    return annotation == null ? null : annotation.value();
  }

  private Class<?> findController(String simpleName) throws Exception {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
    return scanner.findCandidateComponents("com.fabricmanagement.sales").stream()
        .map(component -> component.getBeanClassName())
        .filter(name -> name.endsWith("." + simpleName))
        .map(
            name -> {
              try {
                return Class.forName(name);
              } catch (ClassNotFoundException exception) {
                throw new IllegalStateException(exception);
              }
            })
        .findFirst()
        .orElseThrow();
  }

  private boolean isMapped(Method method) {
    return AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)
        || AnnotatedElementUtils.hasAnnotation(method, GetMapping.class)
        || AnnotatedElementUtils.hasAnnotation(method, PostMapping.class)
        || AnnotatedElementUtils.hasAnnotation(method, PutMapping.class)
        || AnnotatedElementUtils.hasAnnotation(method, PatchMapping.class)
        || AnnotatedElementUtils.hasAnnotation(method, DeleteMapping.class);
  }

  private String key(Class<?> controller, Method method) {
    String parameters =
        Arrays.stream(method.getParameterTypes())
            .map(Class::getName)
            .collect(java.util.stream.Collectors.joining(","));
    return controller.getName() + "#" + method.getName() + "(" + parameters + ")";
  }

  private Map<String, Category> expectedClassifications() {
    Map<String, Category> result = new LinkedHashMap<>();
    add(
        result,
        "QuoteController",
        Category.TRANSACTIONAL_READ,
        "listQuotes",
        "getStatusCounts",
        "getQuote");
    add(result, "QuoteController", Category.CREATE, "createQuote");
    add(
        result,
        "QuoteController",
        Category.MUTATION,
        "addLine",
        "updateQuote",
        "updateLine",
        "removeLine",
        "submitQuote",
        "sendQuote",
        "approveSendRequest",
        "rejectSendRequest",
        "reviseQuote",
        "generateToken");
    add(result, "PublicQuoteController", Category.PUBLIC_TOKEN, "getByToken", "approve");
    add(
        result,
        "SampleManagementController",
        Category.TRANSACTIONAL_READ,
        "listSampleRequests",
        "getSampleRequest");
    add(result, "SampleManagementController", Category.CREATE, "requestSample");
    add(result, "SampleManagementController", Category.MUTATION, "dispatchSample", "markDelivered");
    add(result, "SalesProductController", Category.REFERENCE_READ, "listCatalog", "getByProduct");
    add(
        result,
        "SalesProductController",
        Category.CATALOGUE_MANAGEMENT,
        "createEntry",
        "deactivateEntry");
    add(result, "SalesColorController", Category.REFERENCE_READ, "listSalesColors");
    add(result, "DeliveryCommitmentController", Category.REFERENCE_READ, "listDeliveryTerms");
    add(
        result,
        "OrderFlowController",
        Category.TRANSACTIONAL_READ,
        "getOrderFlow",
        "listOrdersWithPlanning",
        "listOrderPlannerCandidates",
        "getOrderPlanningHistory");
    add(
        result,
        "OrderFlowController",
        Category.MUTATION,
        "submitOrderToPlanning",
        "withdrawOrderToDraft",
        "startOrderEvaluation",
        "proposeOrderDelivery",
        "completeOrderPlanning",
        "returnOrderToSales",
        "claimOrderEvaluation",
        "assignOrderPlanner",
        "releaseOrderPlanner",
        "reopenOrderEvaluation");
    add(
        result,
        "DeliveryCommitmentController",
        Category.TRANSACTIONAL_READ,
        "getDeliveryCommitments");
    add(result, "SalesQualityGradeController", Category.REFERENCE_READ, "listSalesGrades");
    add(result, "SalesLotController", Category.REFERENCE_READ, "listSalesLots");
    add(
        result,
        "CustomerAccountTeamController",
        Category.OWNERSHIP_OPERATION,
        "getAccountTeam",
        "listCandidates",
        "addMember",
        "deactivateMember");
    add(
        result,
        "CustomerCommercialAssignmentController",
        Category.OWNERSHIP_OPERATION,
        "getCurrent",
        "getHistory",
        "assignPrimary");
    add(result, "OwnershipTriageController", Category.OWNERSHIP_OPERATION, "list", "resolve");
    add(
        result,
        "SalesOrderController",
        Category.TRANSACTIONAL_READ,
        "getOrder",
        "getOrderByNumber",
        "getAllOrders",
        "getOrdersByPartner",
        "getOrdersByStatus",
        "getOpenOrders",
        "getOverdueOrders");
    add(result, "SalesOrderController", Category.CREATE, "createOrder");
    add(
        result,
        "OrderCoverController",
        Category.TRANSACTIONAL_READ,
        "getOrderCoverCase",
        "getOrderCoverResult",
        // POST only because the selection is a body; it reads and writes nothing (DETAIL-2 §4).
        "previewOrderCoverSelection");
    add(result, "OrderCoverController", Category.MUTATION, "refreshOrderCoverEvidence");
    add(
        result,
        "SalesOrderController",
        Category.MUTATION,
        "updateOrder",
        "deleteOrder",
        "startProcessing",
        "shipOrder",
        "deliverOrder",
        "cancelOrder",
        "holdOrder",
        "resumeOrder",
        "reviseOrder");
    // SOI order intake (D1-D8).
    add(result, "OrderIntakeProductController", Category.REFERENCE_READ, "search");
    add(result, "OrderIntakeAvailabilityController", Category.REFERENCE_READ, "get");
    add(
        result,
        "OrderIntakeController",
        Category.TRANSACTIONAL_READ,
        "getLatestQuantityProposal",
        "listToneAcceptances",
        "listQuantityAcceptances",
        "getOrderIntakeReadiness");
    add(
        result,
        "OrderIntakeController",
        Category.MUTATION,
        "evaluateLineQuantity",
        "recordToneAcceptance",
        "recordQuantityAcceptance",
        "withdrawQuantityAcceptance");
    add(
        result,
        "CustomerRequestController",
        Category.TRANSACTIONAL_READ,
        "listIntakeAttachments",
        "downloadIntakeAttachment",
        "listCustomerProductRequests",
        "listUnattachedCustomerProductRequests",
        "getPartialDeliveryPreference");
    add(
        result,
        "CustomerRequestController",
        Category.CREATE,
        "uploadIntakeAttachment",
        "createCustomerProductRequest");
    add(
        result,
        "CustomerRequestController",
        Category.MUTATION,
        "updateCustomerProductRequest",
        "attachCustomerProductRequest",
        "detachCustomerProductRequest",
        "closeCustomerProductRequest",
        "markCustomerRequestRevisionSent",
        "recordCustomerRequestDecision",
        "resolveCustomerProductRequest",
        "recordPartialDeliveryPreference");
    add(
        result,
        "CustomerRequestEvaluationController",
        Category.TRANSACTIONAL_READ,
        "evaluationQueue",
        "getForEvaluation",
        "downloadAttachment");
    add(result, "CustomerRequestEvaluationController", Category.MUTATION, "evaluate", "propose");
    add(
        result,
        "OrderFulfilmentController",
        Category.TRANSACTIONAL_READ,
        "getOrderDeliveryOutlook",
        "listLineProductCorrections",
        "listLineHolds");
    add(
        result,
        "OrderFulfilmentController",
        Category.MUTATION,
        "requestPortionReadiness",
        "correctLineProduct",
        "requestLineHold");
    add(
        result,
        "FulfilmentPlanningController",
        Category.TRANSACTIONAL_READ,
        "listProductionReadinessRequests",
        "listWarehouseReadinessRequests",
        "listArrivalWork");
    add(
        result,
        "OrderWorkController",
        Category.TRANSACTIONAL_READ,
        "getOrderLogisticsWork",
        "listOrderLogisticsWorkCandidates");
    add(
        result,
        "OrderWorkController",
        Category.MUTATION,
        "claimOrderLogisticsWork",
        "assignOrderLogisticsWork",
        "releaseOrderLogisticsWork");
    add(
        result,
        "FulfilmentPlanningController",
        Category.MUTATION,
        "confirmGreigeCover",
        "withdrawGreigeCover",
        "confirmProductionReadiness",
        "confirmShipReadiness",
        "recordArrivalEstimate");
    // SOI C: sending the order for the customer's approval; the customer's public page.
    add(
        result,
        "CustomerApprovalController",
        Category.TRANSACTIONAL_READ,
        "getCustomerApproval",
        "listCustomerChangeRequests");
    add(
        result,
        "CustomerApprovalController",
        Category.MUTATION,
        "sendOrderInformation",
        "sendOrderForApproval",
        "resendOrderApprovalLink");
    add(
        result,
        "PublicOrderApprovalController",
        Category.PUBLIC_TOKEN,
        "getOrderApprovalLink",
        "sendOrderApprovalCode",
        "verifyOrderApprovalCode",
        "getOrderApprovalVersion",
        "approveOrderVersion",
        "requestOrderChanges");
    return Map.copyOf(result);
  }

  private void add(
      Map<String, Category> target, String controller, Category category, String... methods) {
    for (String method : methods) {
      Class<?> controllerClass;
      try {
        controllerClass = findController(controller);
      } catch (Exception exception) {
        throw new IllegalStateException("Cannot resolve sales controller " + controller, exception);
      }
      List<Method> mappedOverloads =
          Arrays.stream(controllerClass.getMethods())
              .filter(candidate -> candidate.getName().equals(method))
              .filter(this::isMapped)
              .toList();
      if (mappedOverloads.size() != 1) {
        throw new IllegalStateException(
            controller
                + "#"
                + method
                + " must resolve to exactly one mapped signature but found "
                + mappedOverloads.size());
      }
      String signature = key(controllerClass, mappedOverloads.getFirst());
      Category previous = target.put(signature, category);
      if (previous != null) {
        throw new IllegalStateException(signature + " has two categories");
      }
    }
  }
}
