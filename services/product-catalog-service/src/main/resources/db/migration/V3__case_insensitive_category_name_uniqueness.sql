-- The app enforces category-name uniqueness case-insensitively
-- (CategoryRepository.existsByNameIgnoreCase), but V1's plain UNIQUE(name)
-- constraint is case-sensitive, so two concurrent creates differing only in
-- case (e.g. "Books" vs "books") could both succeed. A functional unique
-- index on lower(name) closes that gap and gives existsByNameIgnoreCase's
-- generated UPPER/LOWER comparison an index to actually use.
CREATE UNIQUE INDEX idx_categories_name_lower_unique ON categories (lower(name));
