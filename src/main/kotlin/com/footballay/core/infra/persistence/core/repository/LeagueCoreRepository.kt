package com.footballay.core.infra.persistence.core.repository

import com.footballay.core.domain.league.MatchCollect
import com.footballay.core.infra.persistence.core.entity.LeagueCore
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface LeagueCoreRepository : JpaRepository<LeagueCore, Long> {
    fun findByAvailableTrue(): List<LeagueCore>

    fun findByAvailableTrueAndMatchCollect(matchCollect: MatchCollect): List<LeagueCore>

    fun findByUid(uid: String): LeagueCore?
}
