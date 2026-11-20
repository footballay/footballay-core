package com.footballay.core.infra.persistence.core.repository

import com.footballay.core.infra.persistence.core.entity.PlayerCore
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PlayerCoreRepository : JpaRepository<PlayerCore, Long> {
    fun findByUid(uid: String): PlayerCore?
}
