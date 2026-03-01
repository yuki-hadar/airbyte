/*
 * Copyright (c) 2026 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.destination.snowflake.spec

import io.airbyte.cdk.ConfigErrorException
import io.airbyte.cdk.load.command.DestinationConfiguration
import io.airbyte.cdk.load.command.DestinationConfigurationFactory
import io.airbyte.cdk.load.table.DEFAULT_AIRBYTE_INTERNAL_NAMESPACE
import jakarta.inject.Singleton

data class SnowflakeConfiguration(
    val host: String,
    val role: String,
    val warehouse: String,
    val database: String,
    val schema: String,
    val username: String,
    val authType: AuthTypeConfiguration,
    val cdcDeletionMode: CdcDeletionMode,
    val legacyRawTablesOnly: Boolean,
    val internalTableSchema: String,
    val jdbcUrlParams: String?,
    val retentionPeriodDays: Int,
) : DestinationConfiguration()

sealed interface AuthTypeConfiguration

data class KeyPairAuthConfiguration(
    val privateKey: String,
    val privateKeyPassword: String?,
) : AuthTypeConfiguration

data class UsernamePasswordAuthConfiguration(
    val password: String,
) : AuthTypeConfiguration

@Singleton
class SnowflakeConfigurationFactory :
    DestinationConfigurationFactory<SnowflakeSpecification, SnowflakeConfiguration> {
    
    companion object {
        private val SNOWFLAKE_HOST_PATTERN = "^.*\\.(snowflakecomputing\\.com|localstack\\.cloud)$".toRegex(RegexOption.IGNORE_CASE)
    }
    
    override fun makeWithoutExceptionHandling(
        pojo: SnowflakeSpecification
    ): SnowflakeConfiguration {
        // Validate host against allowed domains unless custom host is explicitly enabled
        val useCustomHost = pojo.useCustomHost ?: false
        if (!useCustomHost && !SNOWFLAKE_HOST_PATTERN.matches(pojo.host)) {
            throw ConfigErrorException(
                "Host '${pojo.host}' must end with snowflakecomputing.com or localstack.cloud. " +
                "If you need to use a custom proxy or gateway, enable the 'Use Custom Host' option."
            )
        }
        
        val authTypeConfig =
            when (pojo.credentials) {
                is KeyPairAuthSpecification -> {
                    // Despite what Kotlin thinks, this cast is necessary
                    @Suppress("USELESS_CAST")
                    val keyPairAuthSpec = pojo.credentials as KeyPairAuthSpecification
                    KeyPairAuthConfiguration(
                        keyPairAuthSpec.privateKey,
                        keyPairAuthSpec.privateKeyPassword
                    )
                }
                is UsernamePasswordAuthSpecification -> {
                    // Despite what Kotlin thinks, this cast is necessary
                    @Suppress("USELESS_CAST")
                    val usernamePasswordAuthSpec =
                        pojo.credentials as UsernamePasswordAuthSpecification
                    UsernamePasswordAuthConfiguration(usernamePasswordAuthSpec.password)
                }
                null -> {
                    UsernamePasswordAuthConfiguration("")
                }
            }

        return SnowflakeConfiguration(
            host = pojo.host,
            role = pojo.role,
            warehouse = pojo.warehouse,
            database = pojo.database,
            schema = pojo.schema,
            username = pojo.username,
            authType = authTypeConfig,
            cdcDeletionMode = pojo.cdcDeletionMode ?: CdcDeletionMode.HARD_DELETE,
            legacyRawTablesOnly = pojo.legacyRawTablesOnly ?: false,
            internalTableSchema =
                if (pojo.internalTableSchema.isNullOrBlank()) {
                    DEFAULT_AIRBYTE_INTERNAL_NAMESPACE
                } else {
                    pojo.internalTableSchema!!
                },
            jdbcUrlParams = pojo.jdbcUrlParams,
            retentionPeriodDays = pojo.retentionPeriodDays ?: 1
        )
    }
}
