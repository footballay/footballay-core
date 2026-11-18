package com.footballay.core.infra.persistence.core.repository

import com.footballay.core.infra.persistence.core.entity.PlayerCore
import com.footballay.core.infra.persistence.core.read.PlayerNameRow
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PlayerCoreRepository : JpaRepository<PlayerCore, Long> {
    fun findByUid(uid: String): PlayerCore?

    /** 요청한 UID 중 존재하는 선수의 UID 와 이름만 조회합니다. */
    @Query(
        """
        SELECT new com.footballay.core.infra.persistence.core.read.PlayerNameRow(p.uid, p.name)
        FROM PlayerCore p
        WHERE p.uid IN :uids
        """,
    )
    fun findPlayerNamesByUidIn(
        @Param("uids") uids: Collection<String>,
    ): List<PlayerNameRow>
}
