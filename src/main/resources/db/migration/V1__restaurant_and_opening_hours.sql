CREATE TABLE restaurant_profile (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    display_name VARCHAR(160) NOT NULL CHECK (TRIM(display_name) <> ''),
    description VARCHAR(1000) NOT NULL CHECK (TRIM(description) <> ''),
    address VARCHAR(500) NOT NULL CHECK (TRIM(address) <> ''),
    phone VARCHAR(50) NOT NULL CHECK (TRIM(phone) <> ''),
    email VARCHAR(254),
    google_maps_url VARCHAR(2048) NOT NULL CHECK (TRIM(google_maps_url) <> ''),
    wolt_url VARCHAR(2048),
    bolt_food_url VARCHAR(2048),
    instagram_url VARCHAR(2048),
    facebook_url VARCHAR(2048),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE weekly_opening_hours (
    day_of_week VARCHAR(9) PRIMARY KEY CHECK (day_of_week IN
        ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')),
    is_open BOOLEAN NOT NULL,
    opening_time TIME,
    closing_time TIME,
    CONSTRAINT weekly_hours_state CHECK (
        (is_open = FALSE AND opening_time IS NULL AND closing_time IS NULL)
        OR (is_open = TRUE AND opening_time IS NOT NULL AND closing_time IS NOT NULL
            AND closing_time > opening_time)
    )
);

CREATE TABLE special_opening_hours (
    special_date DATE PRIMARY KEY,
    is_open BOOLEAN NOT NULL,
    opening_time TIME,
    closing_time TIME,
    CONSTRAINT special_hours_state CHECK (
        (is_open = FALSE AND opening_time IS NULL AND closing_time IS NULL)
        OR (is_open = TRUE AND opening_time IS NOT NULL AND closing_time IS NOT NULL
            AND closing_time > opening_time)
    )
);
