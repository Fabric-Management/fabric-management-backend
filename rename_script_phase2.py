import os

def replace_in_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    new_content = content.replace('ProductCatalog', 'SalesProduct')
    new_content = new_content.replace('productCatalog', 'salesProduct')
    new_content = new_content.replace('PRODUCT_CATALOG', 'SALES_PRODUCT')
    new_content = new_content.replace('product-catalog', 'sales-product')
    new_content = new_content.replace('product_catalog', 'sales_product')

    if content != new_content:
        with open(filepath, 'w') as f:
            f.write(new_content)
        return True
    return False

def rename_files_and_directories(root_dir):
    # First rename contents of all files
    for dirpath, dirnames, filenames in os.walk(root_dir):
        if '.git' in dirpath or 'target' in dirpath:
            continue
        for filename in filenames:
            if filename.endswith('.java') or filename.endswith('.sql') or filename.endswith('.xml') or filename.endswith('.yaml') or filename.endswith('.yml') or filename.endswith('.json'):
                filepath = os.path.join(dirpath, filename)
                replace_in_file(filepath)

    # Then rename files
    for dirpath, dirnames, filenames in os.walk(root_dir, topdown=False):
        if '.git' in dirpath or 'target' in dirpath:
            continue
        for filename in filenames:
            new_filename = filename.replace('ProductCatalog', 'SalesProduct').replace('productCatalog', 'salesProduct').replace('PRODUCT_CATALOG', 'SALES_PRODUCT').replace('product-catalog', 'sales-product').replace('product_catalog', 'sales_product')
            if filename != new_filename:
                os.rename(os.path.join(dirpath, filename), os.path.join(dirpath, new_filename))

    # Then rename directories
    for dirpath, dirnames, filenames in os.walk(root_dir, topdown=False):
        if '.git' in dirpath or 'target' in dirpath:
            continue
        for dirname in dirnames:
            new_dirname = dirname.replace('ProductCatalog', 'SalesProduct').replace('productCatalog', 'salesProduct').replace('PRODUCT_CATALOG', 'SALES_PRODUCT').replace('product-catalog', 'sales-product').replace('product_catalog', 'sales_product')
            if dirname != new_dirname:
                os.rename(os.path.join(dirpath, dirname), os.path.join(dirpath, new_dirname))

if __name__ == "__main__":
    rename_files_and_directories('/Users/user/Coding/fabric-management/fabric-management-backend/src')
