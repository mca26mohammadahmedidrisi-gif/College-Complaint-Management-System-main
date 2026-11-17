-- Sample reference data. Run after schema.sql. Inserts are safe to re-run.

INSERT INTO departments (department_name, description) VALUES
    ('Facilities', 'Building maintenance, cleanliness, plumbing, and electrical work'),
    ('Information Technology', 'Computer laboratories, internet, and network support'),
    ('Student Affairs', 'Student services and general campus support')
ON CONFLICT (department_name) DO NOTHING;

INSERT INTO buildings (building_name) VALUES
    ('Main Building'),
    ('Science Building')
ON CONFLICT (building_name) DO NOTHING;

INSERT INTO floors (building_id, floor_number, floor_name)
SELECT b.building_id, f.floor_number, f.floor_name
FROM (VALUES
    ('Main Building', 0, 'Ground Floor'),
    ('Main Building', 1, 'First Floor'),
    ('Main Building', 2, 'Second Floor'),
    ('Science Building', 0, 'Ground Floor')
) AS f(building_name, floor_number, floor_name)
JOIN buildings b ON b.building_name = f.building_name
ON CONFLICT (building_id, floor_number) DO UPDATE
SET floor_name = EXCLUDED.floor_name;

INSERT INTO classrooms (floor_id, classroom_number, classroom_name)
SELECT f.floor_id, c.classroom_number, c.classroom_name
FROM (VALUES
    ('Main Building', 0, '101', 'Room 101'),
    ('Main Building', 0, '102', 'Room 102'),
    ('Main Building', 0, '103', 'Computer Lab 103'),
    ('Main Building', 0, 'COMMON', 'Other / Common Area'),
    ('Main Building', 1, '201', 'Room 201'),
    ('Main Building', 1, '202', 'Room 202'),
    ('Main Building', 1, '203', 'Room 203'),
    ('Main Building', 1, 'COMMON', 'Other / Common Area'),
    ('Main Building', 2, '301', 'Room 301'),
    ('Main Building', 2, '302', 'Room 302'),
    ('Main Building', 2, 'COMMON', 'Other / Common Area'),
    ('Science Building', 0, 'S101', 'Science Lab 101'),
    ('Science Building', 0, 'S102', 'Science Lab 102'),
    ('Science Building', 0, 'COMMON', 'Other / Common Area')
) AS c(building_name, floor_number, classroom_number, classroom_name)
JOIN buildings b ON b.building_name = c.building_name
JOIN floors f ON f.building_id = b.building_id AND f.floor_number = c.floor_number
ON CONFLICT (floor_id, classroom_number) DO UPDATE
SET classroom_name = EXCLUDED.classroom_name;

INSERT INTO complaint_categories (department_id, category_name)
SELECT d.department_id, c.category_name
FROM (VALUES
    ('Facilities', 'Electrical Problems'),
    ('Facilities', 'Plumbing Problems'),
    ('Facilities', 'Classroom Maintenance'),
    ('Information Technology', 'Computer and Laboratory Issues'),
    ('Facilities', 'Furniture Problems'),
    ('Facilities', 'Cleanliness'),
    ('Facilities', 'Washroom Problems'),
    ('Facilities', 'Water Supply'),
    ('Information Technology', 'Internet and Network Issues'),
    ('Student Affairs', 'Other')
) AS c(department_name, category_name)
LEFT JOIN departments d ON d.department_name = c.department_name
ON CONFLICT (category_name) DO UPDATE
SET department_id = COALESCE(complaint_categories.department_id, EXCLUDED.department_id);
