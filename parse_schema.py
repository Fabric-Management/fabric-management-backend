import re

with open('src/main/resources/db/migration/V001__initial_schema.sql', 'r') as f:
    text = f.read()

# find all CREATE TABLE blocks
blocks = re.findall(r'CREATE TABLE IF NOT EXISTS\s+([^\s\(]+)(.*?)(?=CREATE TABLE|\Z)', text, re.DOTALL | re.IGNORECASE)

for table_name, block_content in blocks:
    lines = block_content.split('\n')
    for line in lines:
        if "DEFAULT 'TRY'" in line:
            # extract column name
            col_match = re.match(r'^\s*([a-zA-Z_0-9]+)\s+', line)
            if col_match:
                print(f"ALTER TABLE {table_name} ALTER COLUMN {col_match.group(1)} SET DEFAULT 'GBP';")
