insert into round_user(user_id, round_id, role, active)
select distinct jury_id as user_id, round_id, 'jury' as role, true as active from selection