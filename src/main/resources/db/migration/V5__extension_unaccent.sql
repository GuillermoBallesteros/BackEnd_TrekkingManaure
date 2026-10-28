-- HU-04: búsqueda sin distinguir tildes. unaccent es una extensión "trusted" (PostgreSQL 13+),
-- por lo que un usuario no superusuario con CREATE sobre la base de datos puede crearla.
CREATE EXTENSION IF NOT EXISTS unaccent;
