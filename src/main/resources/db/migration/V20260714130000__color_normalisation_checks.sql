-- Domain normalisation is also a persistence invariant. This migration deliberately performs no
-- UPDATE: tenant-owned values must never be rewritten without an explicit business decision.

DO $$
DECLARE
  bad_code_canonical      BIGINT;
  bad_code_blank          BIGINT;
  bad_name_canonical      BIGINT;
  bad_name_blank          BIGINT;
  bad_pantone_canonical   BIGINT;
  bad_pantone_blank       BIGINT;
  bad_hex_case            BIGINT;
BEGIN
  SELECT
    count(*) FILTER (WHERE code <> upper(code) OR code <> btrim(code)),
    count(*) FILTER (WHERE code = ''),
    count(*) FILTER (WHERE name <> btrim(name)),
    count(*) FILTER (WHERE name = ''),
    count(*) FILTER (
      WHERE pantone_code IS NOT NULL
        AND (pantone_code <> upper(pantone_code) OR pantone_code <> btrim(pantone_code))),
    count(*) FILTER (WHERE pantone_code = ''),
    count(*) FILTER (WHERE color_hex IS NOT NULL AND color_hex <> upper(color_hex))
  INTO
    bad_code_canonical,
    bad_code_blank,
    bad_name_canonical,
    bad_name_blank,
    bad_pantone_canonical,
    bad_pantone_blank,
    bad_hex_case
  FROM production.color;

  IF bad_code_canonical > 0
      OR bad_code_blank > 0
      OR bad_name_canonical > 0
      OR bad_name_blank > 0
      OR bad_pantone_canonical > 0
      OR bad_pantone_blank > 0
      OR bad_hex_case > 0 THEN
    RAISE EXCEPTION USING
      MESSAGE = format(
        'Color normalisation preflight failed: code_canonical=%s, code_blank=%s, '
        'name_canonical=%s, name_blank=%s, pantone_canonical=%s, pantone_blank=%s, '
        'hex_uppercase=%s. No rows were changed.',
        bad_code_canonical,
        bad_code_blank,
        bad_name_canonical,
        bad_name_blank,
        bad_pantone_canonical,
        bad_pantone_blank,
        bad_hex_case);
  END IF;
END $$;

ALTER TABLE production.color
  DROP CONSTRAINT IF EXISTS chk_color_code_canonical,
  DROP CONSTRAINT IF EXISTS chk_color_code_not_blank,
  DROP CONSTRAINT IF EXISTS chk_color_name_canonical,
  DROP CONSTRAINT IF EXISTS chk_color_name_not_blank,
  DROP CONSTRAINT IF EXISTS chk_color_pantone_code_canonical,
  DROP CONSTRAINT IF EXISTS chk_color_pantone_code_not_blank,
  DROP CONSTRAINT IF EXISTS chk_color_hex_uppercase;

ALTER TABLE production.color
  ADD CONSTRAINT chk_color_code_canonical
    CHECK (code = upper(code) AND code = btrim(code)),
  ADD CONSTRAINT chk_color_code_not_blank
    CHECK (code <> ''),
  ADD CONSTRAINT chk_color_name_canonical
    CHECK (name = btrim(name)),
  ADD CONSTRAINT chk_color_name_not_blank
    CHECK (name <> ''),
  ADD CONSTRAINT chk_color_pantone_code_canonical
    CHECK (
      pantone_code IS NULL
      OR (pantone_code = upper(pantone_code) AND pantone_code = btrim(pantone_code))),
  ADD CONSTRAINT chk_color_pantone_code_not_blank
    CHECK (pantone_code IS NULL OR pantone_code <> ''),
  ADD CONSTRAINT chk_color_hex_uppercase
    CHECK (color_hex IS NULL OR color_hex = upper(color_hex));
