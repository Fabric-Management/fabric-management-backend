import os

test_files = [
    "src/test/java/com/fabricmanagement/procurement/quote/app/listener/QuoteToOrderOrchestratorTest.java",
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

for root, dirs, files in os.walk("src/test/java"):
    for file in files:
        if file.endswith("Test.java"):
            path = os.path.join(root, file)
            with open(path, "r") as f:
                content = f.read()
            if "IdempotentEventHandler" in content:
                continue
            
            # check if the corresponding prod class has IdempotentEventHandler
            # e.g. path is src/test/java/com/foo/BarTest.java
            # prod class is src/main/java/com/foo/Bar.java
            prod_path = path.replace("src/test/", "src/main/").replace("Test.java", ".java")
            if not os.path.exists(prod_path):
                continue
            with open(prod_path, "r") as f:
                prod_content = f.read()
            if "IdempotentEventHandler" not in prod_content:
                continue
                
            idx = content.find("@InjectMocks")
            if idx == -1:
                continue
            eol = content.find("\n", idx)
            new_content = content[:eol+1] + patch_code + content[eol+1:]
            with open(path, "w") as f:
                f.write(new_content)
                print("Patched", path)
