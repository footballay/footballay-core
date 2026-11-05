package com.footballay.core.web.admin.apisports.service

import com.footballay.core.infra.persistence.apisports.repository.LeagueApiSportsRepository
import com.footballay.core.web.admin.apisports.dto.AvailableLeagueDto
import com.footballay.core.web.admin.apisports.mapper.LeagueWebMapper
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 관리자용 League 조회 서비스 구현체
 *
 * **주의**
 * 이 서비스는 Admin 전용 단순 조회이므로 Facade 없이 곧장 Repository를 호출합니다
 * 비즈니스 로직이 복잡하게 추가된다면 Domain Facade 내부로 옮기고 분리하세요
 */
@Service
class AdminLeagueQueryWebServiceImpl(
    private val leagueApiSportsRepository: LeagueApiSportsRepository,
) : AdminLeagueQueryWebService {
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    override fun findAvailableLeagues(): List<AvailableLeagueDto> {
        return leagueApiSportsRepository.findByAvailableCore().map { api ->
            LeagueWebMapper.toAvailableDto(requireNotNull(api.leagueCore), api)
        }
    }
}
