import os
import re

def replace_in_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    new_content = content.replace('Material', 'Product')
    new_content = new_content.replace('material', 'product')
    new_content = new_content.replace('MATERIAL', 'PRODUCT')

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
            if 'Material' in filename or 'material' in filename or 'MATERIAL' in filename:
                new_filename = filename.replace('Material', 'Product').replace('material', 'product').replace('MATERIAL', 'PRODUCT')
                os.rename(os.path.join(dirpath, filename), os.path.join(dirpath, new_filename))

    # Then rename directories
    for dirpath, dirnames, filenames in os.walk(root_dir, topdown=False):
        if '.git' in dirpath or 'target' in dirpath:
            continue
        for dirname in dirnames:
            if 'Material' in dirname or 'material' in dirname or 'MATERIAL' in dirname:
                new_dirname = dirname.replace('Material', 'Product').replace('material', 'product').replace('MATERIAL', 'PRODUCT')
                os.rename(os.path.join(dirpath, dirname), os.path.join(dirpath, new_dirname))

if __name__ == "__main__":
    rename_files_and_directories('/Users/user/Coding/fabric-management/fabric-management-backend/src')
