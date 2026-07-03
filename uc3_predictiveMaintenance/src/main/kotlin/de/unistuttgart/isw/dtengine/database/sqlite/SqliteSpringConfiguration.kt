package de.unistuttgart.isw.dtengine.database.sqlite

import de.unistuttgart.isw.dtengine.core.ComponentId
import kotlinx.coroutines.runBlocking
import org.sqlite.SQLiteDataSource
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import java.nio.file.Files
import java.nio.file.Path
import javax.sql.DataSource

@ConfigurationProperties(prefix = "dt.sqlite")
class SqliteProperties {
    var enabled: Boolean = false
    var autoStart: Boolean = false
    var componentId: String = "sqlite-db"
    var modelManagerComponentId: String = "sqlite-model-manager"
    var description: String = "SQLite DT data store"
    var modelManagerDescription: String = "SQLite-backed model manager"
    var jdbcUrl: String = "jdbc:sqlite:./data/dt-engine.sqlite"
    var autoCreatePropertiesFromGateway: Boolean = true
    var seedMqttCatalog: Boolean = false
    var seedMqttWritableCommands: Boolean = true
    var aasRepositoryEnabled: Boolean = true
}

@Configuration
@EnableConfigurationProperties(SqliteProperties::class)
class SqliteSpringConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "dt.sqlite", name = ["enabled"], havingValue = "true")
    fun sqliteDataSource(properties: SqliteProperties): DataSource {
        ensureSqliteDirectoryExists(properties.jdbcUrl)
        return SQLiteDataSource().apply { setUrl(properties.jdbcUrl) }
    }

    @Bean
    @ConditionalOnBean(name = ["sqliteDataSource"])
    fun sqliteJdbcTemplate(sqliteDataSource: DataSource): JdbcTemplate = JdbcTemplate(sqliteDataSource)

    @Bean
    @ConditionalOnBean(JdbcTemplate::class)
    fun sqliteDtStore(properties: SqliteProperties, sqliteJdbcTemplate: JdbcTemplate): SqliteDtStore = SqliteDtStore(
        id = ComponentId(properties.componentId),
        description = properties.description,
        jdbcTemplate = sqliteJdbcTemplate
    )

    @Bean
    @ConditionalOnBean(JdbcTemplate::class)
    fun sqliteModelManager(properties: SqliteProperties, sqliteJdbcTemplate: JdbcTemplate): SqliteModelManager = SqliteModelManager(
        id = ComponentId(properties.modelManagerComponentId),
        description = properties.modelManagerDescription,
        jdbcTemplate = sqliteJdbcTemplate,
        autoCreatePropertiesFromGateway = properties.autoCreatePropertiesFromGateway
    )

    @Bean
    @Primary
    @ConditionalOnBean(JdbcTemplate::class)
    fun sqliteMappingRegistry(sqliteJdbcTemplate: JdbcTemplate): SqliteMappingRegistry = SqliteMappingRegistry(sqliteJdbcTemplate)

    @Bean
    @ConditionalOnBean(JdbcTemplate::class)
    fun sqliteEventStore(sqliteJdbcTemplate: JdbcTemplate): SqliteEventStore = SqliteEventStore(sqliteJdbcTemplate)

    @Bean
    @ConditionalOnBean(JdbcTemplate::class)
    @Primary
    @ConditionalOnProperty(prefix = "dt.sqlite", name = ["aas-repository-enabled"], havingValue = "true", matchIfMissing = true)
    fun sqliteAasRepositoryClient(sqliteJdbcTemplate: JdbcTemplate): SqliteAasRepositoryClient =
        SqliteAasRepositoryClient(sqliteJdbcTemplate)

    @Bean
    @ConditionalOnBean(SqliteDtStore::class, SqliteModelManager::class)
    @ConditionalOnProperty(prefix = "dt.sqlite", name = ["auto-start"], havingValue = "true")
    fun sqliteRunner(store: SqliteDtStore, modelManager: SqliteModelManager): ApplicationRunner = ApplicationRunner {
        runBlocking {
            store.start()
            modelManager.start()
        }
    }

    private fun ensureSqliteDirectoryExists(jdbcUrl: String) {
        if (!jdbcUrl.startsWith("jdbc:sqlite:")) return
        val pathText = jdbcUrl.removePrefix("jdbc:sqlite:")
        if (pathText.isBlank() || pathText == ":memory:") return
        val path = Path.of(pathText)
        path.parent?.let { Files.createDirectories(it) }
    }
}
