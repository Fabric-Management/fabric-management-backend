-- Add name and description to task_template table
ALTER TABLE flowboard.task_template 
ADD COLUMN name VARCHAR(255) NOT NULL DEFAULT 'Untitled Template',
ADD COLUMN description VARCHAR(1000);

-- Removed the dummy default value requirement for future inserts
ALTER TABLE flowboard.task_template ALTER COLUMN name DROP DEFAULT;
