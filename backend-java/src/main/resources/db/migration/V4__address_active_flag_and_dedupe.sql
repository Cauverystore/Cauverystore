-- Address dedupe repair (run against the live database; safe to run more than once).
-- Flyway is disabled (spring.flyway.enabled=false), so this is a manual one-shot. The same work
-- is done by AddressActiveFlagMigrator + AddressDuplicateMergeMigrator on application startup;
-- this SQL is for fixing the data NOW without waiting for a restart.
--
-- Background: before address management existed, checkout saved a fresh address row for every
-- order, so a customer can have N identical rows for the same place. The active_flag soft-delete
-- column then arrived as NULL on all pre-existing rows, which hid them from the dedupe search and
-- made each checkout mint yet another row. This script (1) restores NULL flags, (2) merges each
-- cluster of identical (user, line, pincode) rows into the row with the most orders behind it,
-- repointing orders at the survivor, and (3) carries the default/billing choices over.

BEGIN;

-- 1) Rows that predate the soft-delete column are all live.
UPDATE addresses SET active_flag = true WHERE active_flag IS NULL;

-- 2) Cluster addresses per user + primary line (line1, falling back to street) + pincode;
--    rank each cluster by order usage, then repoint orders and soft-delete the losers.
WITH keyed AS (
    SELECT id, user_id,
           lower(coalesce(nullif(btrim(line1), ''), btrim(street))) AS norm_line,
           lower(btrim(pincode)) AS norm_pincode,
           is_default, is_billing,
           ROW_NUMBER() OVER (
               PARTITION BY user_id,
                            lower(coalesce(nullif(btrim(line1), ''), btrim(street))),
                            lower(btrim(pincode))
               ORDER BY (SELECT count(*) FROM orders o WHERE o.address_id = a.id) DESC,
                        a.created_at ASC NULLS LAST, a.id ASC
           ) AS rn
    FROM addresses a
    WHERE active_flag IS NOT FALSE
      AND nullif(btrim(pincode), '') IS NOT NULL
      AND nullif(coalesce(nullif(btrim(line1), ''), btrim(street)), '') IS NOT NULL
),
survivors AS (SELECT * FROM keyed WHERE rn = 1),
losers     AS (SELECT * FROM keyed WHERE rn > 1)

UPDATE orders o SET address_id = s.id
FROM survivors s
JOIN losers l ON l.user_id = s.user_id
             AND l.norm_line = s.norm_line
             AND l.norm_pincode = s.norm_pincode
WHERE o.address_id = l.id
  AND o.address_id IS DISTINCT FROM s.id;

UPDATE addresses a SET active_flag = false, is_default = false
FROM keyed k
WHERE a.id = k.id AND k.rn > 1;

-- 3) The customer's default/billing choice may have lived on a losing row; carry it over.
UPDATE addresses a SET is_default = true
FROM survivors s
JOIN losers l ON l.user_id = s.user_id
             AND l.norm_line = s.norm_line
             AND l.norm_pincode = s.norm_pincode
WHERE a.id = s.id
  AND l.is_default
  AND NOT coalesce(s.is_default, false);

UPDATE addresses a SET is_billing = true
FROM survivors s
JOIN losers l ON l.user_id = s.user_id
             AND l.norm_line = s.norm_line
             AND l.norm_pincode = s.norm_pincode
WHERE a.id = s.id
  AND l.is_billing
  AND NOT coalesce(s.is_billing, false);

COMMIT;
