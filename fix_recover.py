import os
import re

files_with_recover = [
    "src/main/java/com/fabricmanagement/approval/app/listener/ApprovalEventListener.java",
    "src/main/java/com/fabricmanagement/production/execution/stockunit/app/listener/StockUnitCreatedStorageListener.java",
    "src/main/java/com/fabricmanagement/sales/salesorder/app/listener/SalesOrderProductionProgressListener.java",
    "src/main/java/com/fabricmanagement/sales/salesorder/app/listener/SalesOrderEventListener.java"
]

for file_path in files_with_recover:
    with open(file_path, "r") as f:
        content = f.read()

    # We want to replace the body of the recover methods.
    # A recover method looks like:
    # @Recover
    # public void recoverX(Exception ex, Y event) {
    #   log.error(..., ex);
    # }
    
    # We will use regex to find @Recover methods and replace their body with a throw statement.
    # To be safe, we just replace `log.error(...);` with `log.error(...); throw new RuntimeException("Event processing failed after retries", ex);`
    
    # Let's find all `@Recover` and the closing brace of the method, or just replace `ex);` with `ex);\n    throw new RuntimeException("Event processing failed after retries", ex);`
    # Actually, a safer way is to replace `ex);` if it's inside a `@Recover` method. Since we know they only do `log.error(... ex);`, we can just do a regex.
    
    # regex for `@Recover... ex);` -> `@Recover... ex);\n    throw new RuntimeException("Event processing failed after retries", ex);`
    
    new_content = re.sub(r'(@Recover[\s\S]*?log\.error\([\s\S]*?ex\);)', r'\1\n    throw new RuntimeException("Event processing failed after retries", ex);', content)
    
    with open(file_path, "w") as f:
        f.write(new_content)
        print("Patched", file_path)

