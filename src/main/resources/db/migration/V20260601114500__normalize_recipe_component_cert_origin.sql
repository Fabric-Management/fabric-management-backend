-- Normalize existing certification and origin data in prod_recipe_component.
-- Mirrors RecipeComponent.normalizeFields(): trim, uppercase and convert blank values to NULL.

UPDATE production.prod_recipe_component
   SET certification = NULLIF(UPPER(TRIM(certification)), '')
 WHERE certification IS DISTINCT FROM NULLIF(UPPER(TRIM(certification)), '');

UPDATE production.prod_recipe_component
   SET origin = NULLIF(UPPER(TRIM(origin)), '')
 WHERE origin IS DISTINCT FROM NULLIF(UPPER(TRIM(origin)), '');
