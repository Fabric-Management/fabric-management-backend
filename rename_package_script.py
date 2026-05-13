import os

def replace_in_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    new_content = content.replace('sales.catalog', 'sales.salesproduct')

    if content != new_content:
        with open(filepath, 'w') as f:
            f.write(new_content)
        return True
    return False

def rename_package(root_dir):
    # Rename occurrences in files
    for dirpath, dirnames, filenames in os.walk(root_dir):
        if '.git' in dirpath or 'target' in dirpath:
            continue
        for filename in filenames:
            if filename.endswith('.java') or filename.endswith('.sql') or filename.endswith('.xml') or filename.endswith('.yaml') or filename.endswith('.yml') or filename.endswith('.json'):
                filepath = os.path.join(dirpath, filename)
                replace_in_file(filepath)

    # Rename directory
    old_dir = os.path.join(root_dir, 'main', 'java', 'com', 'fabricmanagement', 'sales', 'catalog')
    new_dir = os.path.join(root_dir, 'main', 'java', 'com', 'fabricmanagement', 'sales', 'salesproduct')
    if os.path.exists(old_dir):
        os.rename(old_dir, new_dir)
        
    old_test_dir = os.path.join(root_dir, 'test', 'java', 'com', 'fabricmanagement', 'sales', 'catalog')
    new_test_dir = os.path.join(root_dir, 'test', 'java', 'com', 'fabricmanagement', 'sales', 'salesproduct')
    if os.path.exists(old_test_dir):
        os.rename(old_test_dir, new_test_dir)

if __name__ == "__main__":
    rename_package('/Users/user/Coding/fabric-management/fabric-management-backend/src')
