import os
import re

test_files = [
    "src/test/java/com/fabricmanagement/flowboard/generator/app/SmartTaskGeneratorListenerTest.java",
    "src/test/java/com/fabricmanagement/sales/quote/app/QuoteToOrderOrchestratorTest.java",
    "src/test/java/com/fabricmanagement/production/execution/workorder/app/listener/WorkOrderPlannedCostListenerTest.java",
    "src/test/java/com/fabricmanagement/production/execution/workorder/app/listener/WorkOrderSalesEventListenerTest.java",
    "src/test/java/com/fabricmanagement/sales/salesorder/app/listener/SalesOrderEventListenerTest.java",
    "src/test/java/com/fabricmanagement/sales/salesorder/app/listener/SalesOrderProductionProgressListenerTest.java",
    "src/test/java/com/fabricmanagement/production/execution/stockunit/app/listener/GoodsReceiptConfirmedEventListenerTest.java",
    "src/test/java/com/fabricmanagement/production/execution/workorder/app/listener/WorkOrderCostBridgeListenerTest.java",
    "src/test/java/com/fabricmanagement/production/execution/workorder/app/listener/WorkOrderApprovalEventListenerTest.java",
    "src/test/java/com/fabricmanagement/sales/salesorder/app/listener/SalesOrderApprovalEventListenerTest.java",
    "src/test/java/com/fabricmanagement/notification/i18n/app/listener/TenantSettingsEventListenerTest.java"
]

patch_code = """
  @org.mockito.Mock
  private com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler idempotentHandler;

  @org.junit.jupiter.api.BeforeEach
  void setUpIdempotentHandler() {
    if (idempotentHandler != null) {
        org.mockito.Mockito.lenient().doAnswer(invocation -> {
          ((Runnable) invocation.getArgument(3)).run();
          return null;
        }).when(idempotentHandler).executeOnce(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
  }
"""

for path in test_files:
    if not os.path.exists(path):
        continue
    with open(path, "r") as f:
        content = f.read()
    
    if "IdempotentEventHandler" in content:
        continue
        
    idx = content.find("@InjectMocks")
    if idx == -1:
        continue
        
    eol = content.find("\n", idx)
    new_content = content[:eol+1] + patch_code + content[eol+1:]
    
    with open(path, "w") as f:
        f.write(new_content)
        print("Patched", path)

