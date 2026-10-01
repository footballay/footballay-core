package com.footballay.core.infra.apisports.shared.dto

data class FixtureApiSportsSyncDto(
    var apiId: Long? = null,
    var referee: String? = null,
    var date: String? = null,
    var timestamp: Long? = null,
    var round: String? = null,
    var homeTeam: TeamOfFixtureApiSportsCreateDto? = null,
    var awayTeam: TeamOfFixtureApiSportsCreateDto? = null,
    // involved values with related entities
    var venue: VenueOfFixtureApiSportsCreateDto? = null,
    var leagueApiId: Long? = null,
    var seasonYear: String? = null,
    // embedded entities
    var status: StatusOfFixtureApiSportsCreateDto? = null,
    var score: ScoreOfFixtureApiSportsCreateDto? = null,
)

data class TeamOfFixtureApiSportsCreateDto(
    var apiId: Long? = null,
    var name: String? = null,
    var logo: String? = null,
)

data class VenueOfFixtureApiSportsCreateDto(
    var apiId: Long? = null,
    var name: String? = null,
    var city: String? = null,
)

data class StatusOfFixtureApiSportsCreateDto(
    var longStatus: String? = null,
    var shortStatus: String? = null,
    var elapsed: Int? = null,
    var extra: Int? = null,
)

data class ScoreOfFixtureApiSportsCreateDto(
    var totalHome: Int? = null,
    var totalAway: Int? = null,
    var halftimeHome: Int? = null,
    var halftimeAway: Int? = null,
    var fulltimeHome: Int? = null,
    var fulltimeAway: Int? = null,
    var extratimeHome: Int? = null,
    var extratimeAway: Int? = null,
    var penaltyHome: Int? = null,
    var penaltyAway: Int? = null,
)
