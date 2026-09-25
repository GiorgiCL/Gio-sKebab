CREATE TABLE admin_account (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    email VARCHAR(254) NOT NULL UNIQUE CHECK (email = LOWER(TRIM(email)) AND TRIM(email) <> ''),
    password_hash VARCHAR(255) NOT NULL CHECK (TRIM(password_hash) <> ''),
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
