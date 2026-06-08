package com.nexters.external.config

import com.zaxxer.hikari.HikariDataSource
import jakarta.persistence.EntityManagerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.datasource.init.DataSourceInitializer
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import java.util.Properties
import javax.sql.DataSource

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = ["com.nexters.external.repository"])
class DatabaseConfig {
    @Value("\${spring.datasource.url}")
    private lateinit var url: String

    @Value("\${spring.datasource.username}")
    private lateinit var username: String

    @Value("\${spring.datasource.password}")
    private lateinit var password: String

    @Value("\${spring.datasource.driver-class-name}")
    private lateinit var driverClassName: String

    @Value("\${spring.datasource.hikari.maximum-pool-size:10}")
    private var maximumPoolSize: Int = 10

    @Value("\${spring.datasource.hikari.minimum-idle:2}")
    private var minimumIdle: Int = 2

    @Value("\${spring.datasource.hikari.connection-timeout:30000}")
    private var connectionTimeout: Long = 30_000

    @Value("\${spring.datasource.hikari.idle-timeout:600000}")
    private var idleTimeout: Long = 600_000

    @Value("\${spring.datasource.hikari.max-lifetime:1800000}")
    private var maxLifetime: Long = 1_800_000

    @Value("\${spring.datasource.hikari.pool-name:newsletter-hikari}")
    private lateinit var poolName: String

    @Value("\${spring.jpa.show-sql:false}")
    private var showSql: Boolean = false

    @Value("\${spring.jpa.properties.hibernate.format_sql:false}")
    private var formatSql: Boolean = false

    @Bean
    fun dataSource(): DataSource {
        val dataSource = HikariDataSource()
        dataSource.driverClassName = driverClassName
        dataSource.jdbcUrl = url
        dataSource.username = username
        dataSource.password = password
        dataSource.maximumPoolSize = maximumPoolSize
        dataSource.minimumIdle = minimumIdle
        dataSource.connectionTimeout = connectionTimeout
        dataSource.idleTimeout = idleTimeout
        dataSource.maxLifetime = maxLifetime
        dataSource.poolName = poolName
        return dataSource
    }

    @Bean
    fun dataSourceInitializer(dataSource: DataSource): DataSourceInitializer {
        val initializer = DataSourceInitializer()
        initializer.setDataSource(dataSource)

        val populator = ResourceDatabasePopulator()
        populator.addScript(ClassPathResource("schema.sql"))
        initializer.setDatabasePopulator(populator)

        return initializer
    }

    @Bean
    fun entityManagerFactory(dataSource: DataSource): LocalContainerEntityManagerFactoryBean {
        val em = LocalContainerEntityManagerFactoryBean()
        em.dataSource = dataSource
        em.setPackagesToScan("com.nexters.external.entity")

        val vendorAdapter = HibernateJpaVendorAdapter()
        em.jpaVendorAdapter = vendorAdapter

        val properties = Properties()
        properties["hibernate.hbm2ddl.auto"] = "none" // Using custom init schema instead
        properties["hibernate.dialect"] = "org.hibernate.dialect.PostgreSQLDialect"
        properties["hibernate.show_sql"] = showSql.toString()
        properties["hibernate.format_sql"] = formatSql.toString()

        em.setJpaProperties(properties)

        return em
    }

    @Bean
    fun transactionManager(entityManagerFactory: EntityManagerFactory): PlatformTransactionManager {
        val transactionManager = JpaTransactionManager()
        transactionManager.entityManagerFactory = entityManagerFactory
        return transactionManager
    }
}
