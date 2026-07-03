package de.unistuttgart.isw.dtengine.database.sqlite

import org.springframework.jdbc.core.JdbcTemplate

object SqliteSchema {
    fun initialize(jdbcTemplate: JdbcTemplate) {
        statements.forEach { jdbcTemplate.execute(it) }
    }

    private val statements = listOf(
        """
        CREATE TABLE IF NOT EXISTS data_points (
            id TEXT PRIMARY KEY,
            source_component_id TEXT,
            value_type TEXT,
            machine_id TEXT,
            topic TEXT,
            description TEXT NOT NULL DEFAULT '',
            metadata_json TEXT NOT NULL DEFAULT '{}',
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS data_values (
            sequence_id INTEGER PRIMARY KEY AUTOINCREMENT,
            data_point_id TEXT NOT NULL,
            value_json TEXT,
            value_type TEXT,
            quality TEXT NOT NULL,
            source_component_id TEXT,
            timestamp TEXT NOT NULL,
            metadata_json TEXT NOT NULL DEFAULT '{}',
            FOREIGN KEY (data_point_id) REFERENCES data_points(id) ON DELETE CASCADE
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_data_values_data_point_timestamp ON data_values(data_point_id, timestamp DESC, sequence_id DESC)",
        """
        CREATE TABLE IF NOT EXISTS model_properties (
            property_id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            data_point_id TEXT,
            value_json TEXT,
            value_type TEXT,
            value_quality TEXT,
            value_timestamp TEXT,
            value_source_component_id TEXT,
            value_metadata_json TEXT NOT NULL DEFAULT '{}',
            declared_type TEXT,
            semantic_id TEXT,
            parent_path TEXT,
            metadata_json TEXT NOT NULL DEFAULT '{}',
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_model_properties_data_point ON model_properties(data_point_id)",
        "CREATE INDEX IF NOT EXISTS idx_model_properties_name ON model_properties(name)",
        """
        CREATE TABLE IF NOT EXISTS aas_shells (
            id TEXT PRIMARY KEY,
            id_short TEXT NOT NULL,
            description TEXT NOT NULL DEFAULT '',
            submodel_ids_json TEXT NOT NULL DEFAULT '[]',
            metadata_json TEXT NOT NULL DEFAULT '{}',
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_aas_shells_id_short ON aas_shells(id_short)",
        """
        CREATE TABLE IF NOT EXISTS aas_submodels (
            id TEXT PRIMARY KEY,
            id_short TEXT NOT NULL,
            semantic_id TEXT,
            description TEXT NOT NULL DEFAULT '',
            metadata_json TEXT NOT NULL DEFAULT '{}',
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_aas_submodels_id_short ON aas_submodels(id_short)",
        """
        CREATE TABLE IF NOT EXISTS aas_shell_submodels (
            shell_id TEXT NOT NULL,
            submodel_id TEXT NOT NULL,
            created_at TEXT NOT NULL,
            PRIMARY KEY (shell_id, submodel_id),
            FOREIGN KEY (shell_id) REFERENCES aas_shells(id) ON DELETE CASCADE,
            FOREIGN KEY (submodel_id) REFERENCES aas_submodels(id) ON DELETE CASCADE
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_aas_shell_submodels_submodel ON aas_shell_submodels(submodel_id)",
        """
        CREATE TABLE IF NOT EXISTS aas_properties (
            external_id TEXT PRIMARY KEY,
            shell_id TEXT NOT NULL,
            submodel_id TEXT NOT NULL,
            id_short_path_json TEXT NOT NULL DEFAULT '[]',
            id_short TEXT NOT NULL,
            value_json TEXT,
            value_type TEXT NOT NULL,
            semantic_id TEXT,
            category TEXT,
            description TEXT NOT NULL DEFAULT '',
            observed_at TEXT NOT NULL,
            metadata_json TEXT NOT NULL DEFAULT '{}',
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL,
            FOREIGN KEY (shell_id) REFERENCES aas_shells(id) ON DELETE CASCADE
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_aas_properties_shell_submodel ON aas_properties(shell_id, submodel_id)",
        "CREATE INDEX IF NOT EXISTS idx_aas_properties_id_short ON aas_properties(id_short)",
        "CREATE INDEX IF NOT EXISTS idx_aas_properties_observed_at ON aas_properties(observed_at DESC)",
        """
        CREATE TABLE IF NOT EXISTS service_requirements (
            service_id TEXT NOT NULL,
            data_point_id TEXT NOT NULL,
            required INTEGER NOT NULL DEFAULT 1,
            description TEXT NOT NULL DEFAULT '',
            metadata_json TEXT NOT NULL DEFAULT '{}',
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL,
            PRIMARY KEY (service_id, data_point_id)
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_service_requirements_service ON service_requirements(service_id)",
        """
        CREATE TABLE IF NOT EXISTS service_registry (
            service_id TEXT PRIMARY KEY,
            component_id TEXT NOT NULL,
            service_type TEXT NOT NULL,
            description TEXT NOT NULL DEFAULT '',
            required_data_points_json TEXT NOT NULL DEFAULT '[]',
            required_model_properties_json TEXT NOT NULL DEFAULT '[]',
            required_functions_json TEXT NOT NULL DEFAULT '[]',
            produced_data_points_json TEXT NOT NULL DEFAULT '[]',
            metadata_json TEXT NOT NULL DEFAULT '{}',
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_service_registry_type ON service_registry(service_type)",
        """
        CREATE TABLE IF NOT EXISTS service_model_requirements (
            service_id TEXT NOT NULL,
            model_property_id TEXT NOT NULL,
            required INTEGER NOT NULL DEFAULT 1,
            description TEXT NOT NULL DEFAULT '',
            metadata_json TEXT NOT NULL DEFAULT '{}',
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL,
            PRIMARY KEY (service_id, model_property_id)
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_service_model_requirements_service ON service_model_requirements(service_id)",
        """
        CREATE TABLE IF NOT EXISTS service_function_requirements (
            service_id TEXT NOT NULL,
            function_name TEXT NOT NULL,
            required INTEGER NOT NULL DEFAULT 1,
            description TEXT NOT NULL DEFAULT '',
            metadata_json TEXT NOT NULL DEFAULT '{}',
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL,
            PRIMARY KEY (service_id, function_name)
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_service_function_requirements_service ON service_function_requirements(service_id)",
        """
        CREATE TABLE IF NOT EXISTS mappings (
            mapping_id TEXT PRIMARY KEY,
            source_type TEXT NOT NULL,
            source_owner TEXT NOT NULL,
            source_json TEXT NOT NULL,
            target_type TEXT NOT NULL,
            target_owner TEXT NOT NULL,
            target_json TEXT NOT NULL,
            direction TEXT NOT NULL,
            transformation_json TEXT NOT NULL,
            enabled INTEGER NOT NULL DEFAULT 1,
            metadata_json TEXT NOT NULL DEFAULT '{}',
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_mappings_source ON mappings(source_type, source_owner)",
        "CREATE INDEX IF NOT EXISTS idx_mappings_target ON mappings(target_type, target_owner)",
        """
        CREATE TABLE IF NOT EXISTS dt_events (
            event_id TEXT PRIMARY KEY,
            event_type TEXT NOT NULL,
            source_component_id TEXT NOT NULL,
            payload_json TEXT NOT NULL DEFAULT '{}',
            timestamp TEXT NOT NULL,
            correlation_id TEXT,
            severity TEXT NOT NULL,
            created_at TEXT NOT NULL
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_dt_events_timestamp ON dt_events(timestamp DESC)",
        "CREATE INDEX IF NOT EXISTS idx_dt_events_type ON dt_events(event_type, timestamp DESC)"
    )
}
