package com.footballay.core.domain.playerform

import java.time.Instant

data class PlayerFormMatch(
    val fixtureUid: String,
    val kickoff: Instant,
    val homeTeamUid: String?,
    val homeTeamName: String?,
    val awayTeamUid: String?,
    val awayTeamName: String?,
    val teamUid: String?,
    val minutesPlayed: Int,
    val rating: Double?,
    val goals: Int?,
    val assists: Int?,
)

data class PlayerForm(
    val playerUid: String,
    val matches: List<PlayerFormMatch>,
    val ratedMatches: Int,
    val averageRating: Double?,
)
