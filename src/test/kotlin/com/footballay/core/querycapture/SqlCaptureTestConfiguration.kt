package com.footballay.core.querycapture

import org.hibernate.cfg.AvailableSettings
import org.hibernate.resource.jdbc.spi.StatementInspector
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

/**
 * Repository 호출에서 Hibernate가 JDBC로 전달하는 SQL template만 테스트에서 수집한다.
 */
class SqlCaptureStatementInspector : StatementInspector {
    private val statements = mutableListOf<String>()

    @Synchronized
    override fun inspect(sql: String): String {
        statements += sql
        return sql
    }

    @Synchronized
    fun clear() {
        statements.clear()
    }

    @Synchronized
    fun captured(): List<String> = statements.toList()
}

/**
 * SQL capture 테스트에서만 StatementInspector를 Hibernate에 등록한다.
 */
@TestConfiguration
class SqlCaptureTestConfiguration {
    @Bean
    fun sqlCaptureStatementInspector(): SqlCaptureStatementInspector = SqlCaptureStatementInspector()

    @Bean
    fun sqlCaptureHibernatePropertiesCustomizer(
        inspector: SqlCaptureStatementInspector,
    ): HibernatePropertiesCustomizer =
        HibernatePropertiesCustomizer { properties ->
            properties[AvailableSettings.STATEMENT_INSPECTOR] = inspector
        }
}
