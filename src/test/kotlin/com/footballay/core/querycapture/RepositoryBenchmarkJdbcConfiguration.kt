package com.footballay.core.querycapture

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.datasource.AbstractDataSource
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import javax.sql.DataSource

/** 수동 벤치마크에서만 실행된 JDBC SQL과 실제 바인딩을 캡처합니다. */
class BenchmarkJdbcRecorder {
    data class Binding(val index: Int, val setter: String, val value: Any?, val jdbcType: String?)
    data class Statement(val sql: String, val bindings: List<Binding>, val phase: String)

    private val current = ThreadLocal<MutableList<Statement>?>()
    var phase: String = "repository"

    fun start() { current.set(mutableListOf()) }
    fun stop(): List<Statement> = current.get().orEmpty().toList().also { current.remove() }

    fun connection(delegate: Connection): Connection = proxy(Connection::class.java) { method, args ->
        val result = invoke(delegate, method, args)
        if (method.name == "prepareStatement" && result is PreparedStatement) {
            statement(result, args[0] as String)
        } else result
    }

    private fun statement(delegate: PreparedStatement, sql: String): PreparedStatement {
        delegate.queryTimeout = 30
        val bindings = sortedMapOf<Int, Binding>()
        return proxy(PreparedStatement::class.java) { method, args ->
            if (method.name == "clearParameters") bindings.clear()
            if (method.name.startsWith("set") && args.size >= 2 && args[0] is Int) {
                val index = args[0] as Int
                val value = if (method.name == "setNull") null else args[1]
                bindings[index] = Binding(index, method.name, jsonValue(value), args.getOrNull(2)?.toString()
                    ?: if (method.name == "setNull") args[1].toString() else null)
            }
            if (method.name in setOf("executeQuery", "execute", "executeUpdate", "executeLargeUpdate")) {
                current.get()?.add(Statement(sql, bindings.values.toList(), phase))
            }
            invoke(delegate, method, args)
        }
    }

    private fun jsonValue(value: Any?): Any? = when (value) {
        null, is String, is Number, is Boolean -> value
        is ByteArray -> error("Binary bindings need an explicit benchmark case")
        else -> value.toString()
    }

    private fun invoke(target: Any, method: Method, args: Array<out Any?>): Any? = try {
        method.invoke(target, *args)
    } catch (ex: InvocationTargetException) {
        throw ex.targetException
    }

    private fun <T> proxy(type: Class<T>, call: (Method, Array<out Any?>) -> Any?): T =
        type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args -> call(method, args ?: emptyArray()) })
}

/** 운영 DataSource와 분리된 로컬 벤치마크 전용 연결입니다. */
@TestConfiguration(proxyBeanMethods = false)
class RepositoryBenchmarkJdbcConfiguration {
    @Bean
    fun benchmarkJdbcRecorder() = BenchmarkJdbcRecorder()

    @Bean
    fun dataSource(recorder: BenchmarkJdbcRecorder): DataSource = object : AbstractDataSource() {
        override fun getConnection(): Connection = recorder.connection(DriverManager.getConnection(
            "jdbc:postgresql://127.0.0.1:55432/footballay_benchmark", "benchmark", "footballay_benchmark_only",
        ))
        override fun getConnection(username: String, password: String): Connection = error("Use benchmark credentials only")
    }
}
