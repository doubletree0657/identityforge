ALTER TABLE permissions
    ADD COLUMN display_name VARCHAR(255),
    ADD COLUMN description TEXT,
    ADD COLUMN category VARCHAR(64),
    ADD COLUMN system_managed BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE permissions
SET display_name = name,
    category = 'Custom'
WHERE display_name IS NULL;
