ALTER TABLE fixture_core
    DROP CONSTRAINT fk_fixture_core_on_league;

DROP INDEX idx_fixture_core_match_collect_scan;

ALTER TABLE fixture_core
    DROP COLUMN league_id;
