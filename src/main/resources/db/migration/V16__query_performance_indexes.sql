CREATE INDEX idx_apisports_match_player_match_team
    ON apisports_match_player (match_team_id);

CREATE INDEX idx_fixture_api_sports_season
    ON fixture_api_sports (season_id);

CREATE INDEX idx_fixture_core_kickoff_id
    ON fixture_core (kickoff DESC NULLS LAST, id DESC);
