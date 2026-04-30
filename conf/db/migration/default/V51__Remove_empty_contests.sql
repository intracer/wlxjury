delete cj FROM contest_jury cj
  LEFT JOIN category_members  cm ON cj.category_id = cm.category_id
  LEFT JOIN users             u  ON cj.id = u.contest_id  AND u.contest_id IS NOT NULL
  LEFT JOIN rounds            r  ON cj.id = r.contest_id  AND r.contest_id IS NOT NULL
  WHERE
     (cj.category_id IS NULL OR cm.category_id IS NULL)
    AND u.contest_id       IS NULL
    AND r.contest_id       IS NULL;