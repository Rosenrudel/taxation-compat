package de.randombyte.taxation

import org.spongepowered.api.Sponge
import org.spongepowered.api.service.economy.account.UniqueAccount
import org.spongepowered.api.service.sql.SqlService
import java.math.BigDecimal
import java.sql.Connection
import java.util.UUID
import javax.sql.DataSource

class Database {
    private var dataSource: DataSource? = null
    private var sqlService: SqlService? = null

    private fun getConnection(): Connection {
        if (dataSource == null) {
            createWithConfigCredentials()
        }

        return dataSource!!.connection
    }

    private fun createWithConfigCredentials() {
        if (sqlService == null) {
            sqlService = Sponge.getServiceManager().provide(SqlService::class.java).get()
        }

        dataSource = sqlService!!.getDataSource(generalConfig.connectionJdbc)
    }

    fun createTables() {
        createTaxationSessionTable()
        createTaxationRuntimeTable()
        createTaxationStatisticTable()
    }

    private fun createTaxationSessionTable() {
        val con = getConnection()

        val sql = """
            CREATE TABLE IF NOT EXISTS taxation_session (
                player_uuid VARCHAR(255) NOT NULL PRIMARY KEY,
                initial_balance DECIMAL(19,2) NOT NULL
            );
        """.trimIndent()

        con.prepareStatement(sql).use { stmt ->
            stmt.executeQuery()
        }.close()

        con.close()
    }

    private fun createTaxationRuntimeTable() {
        val con = getConnection()

        val sql = """
            CREATE TABLE IF NOT EXISTS taxation_runtime (
                config_key VARCHAR(255) NOT NULL PRIMARY KEY,
                config_value VARCHAR(255) NOT NULL
            );
        """.trimIndent()

        con.prepareStatement(sql).use { stmt ->
            stmt.executeQuery()
        }.close()

        con.close()
    }

    private fun createTaxationStatisticTable() {
        val con = getConnection()

        val sql = """
            CREATE TABLE IF NOT EXISTS taxation_statistic (
                player_uuid VARCHAR(255) NOT NULL PRIMARY KEY,
                tax_payed DECIMAL(19,2) NOT NULL
            );
        """.trimIndent()

        con.prepareStatement(sql).use { stmt ->
            stmt.executeQuery()
        }.close()

        con.close()
    }

    fun playerAddStatistic(playerUUID: UUID, addTax: BigDecimal) {
        val con = getConnection()

        val sql = """
            INSERT INTO taxation_statistic (player_uuid, tax_payed)
            VALUES (?, ?)
            ON DUPLICATE KEY
            UPDATE tax_payed=tax_payed+?
        """.trimIndent()

        con.prepareStatement(sql).use { stmt ->
            stmt.setString(1, playerUUID.toString())
            stmt.setBigDecimal(2, addTax)
            stmt.setBigDecimal(3, addTax)
            stmt.executeQuery()
        }.close()

        con.close()
    }

    fun getPlayerStatistics(playerUUID: UUID): BigDecimal {
        val con = getConnection()

        val sql = "SELECT tax_payed FROM taxation_statistic WHERE player_uuid = ?;"
        con.prepareStatement(sql).use { stmt ->
            stmt.setString(1, playerUUID.toString())
            stmt.executeQuery().use { result ->
                while (result.next()) {
                    con.close()
                    return result.getBigDecimal("tax_payed")
                }
            }
        }

        con.close()

        return BigDecimal(0)
    }

    fun setRuntimeConfig(key: String, value: String) {
        val con = getConnection()

        val sql = """
            INSERT INTO taxation_runtime (config_key, config_value)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE config_key = ?;
        """.trimIndent()

        con.prepareStatement(sql).use { stmt ->
            stmt.setString(1, key)
            stmt.setString(2, value)
            stmt.setString(3, key)
            stmt.executeQuery()
        }.close()

        con.close()
    }

    fun removeRuntimeConfig(key: String) {
        val con = getConnection()

        val sql = "DELETE FROM taxation_runtime WHERE config_key = ?;"


        con.prepareStatement(sql).use { stmt ->
            stmt.setString(1, key)
            stmt.executeQuery()
        }.close()

        con.close()
    }

    fun getRuntimeValue(key: String): String? {
        val con = getConnection()

        val sql = "SELECT config_value FROM taxation_runtime WHERE config_key = ?;"
        con.prepareStatement(sql).use { stmt ->
            stmt.setString(1, key)
            stmt.executeQuery().use { result ->
                while (result.next()) {
                    con.close()
                    return result.getString("config_value")
                }
            }
        }

        con.close()

        return null
    }

    fun emptyPlayers() {
        val con = getConnection()

        val sql = "TRUNCATE TABLE taxation_session;"
        con.prepareStatement(sql).use { stmt ->
            stmt.executeQuery()
        }.close()

        con.close()
    }

    fun deletePlayer(playerUUID: UUID) {
        val con = getConnection()

        val sql = "DELETE FROM taxation_session WHERE player_uuid = ?;"
        con.prepareStatement(sql).use { stmt ->
            stmt.setString(1, playerUUID.toString())
            stmt.executeQuery()
        }.close()

        con.close()
    }

    fun getPlayers(): MutableMap<UUID, BigDecimal> {
        val con = getConnection()

        val players = mutableMapOf<UUID, BigDecimal>()

        val sql = "SELECT * FROM taxation_session;"
        con.prepareStatement(sql).use { stmt ->
            stmt.executeQuery().use { result ->
                while (result.next()) {
                    val playerUuid = UUID.fromString(result.getString("player_uuid"))
                    players[playerUuid] = result.getBigDecimal("initial_balance")
                }
            }
        }

        con.close()

        return players
    }

    fun maybeTrackPlayer(player: UniqueAccount, initialBalance: BigDecimal) {
        val con = getConnection()

        val sql = """
             INSERT INTO taxation_session (player_uuid, initial_balance)
                VALUES (?, ?)
                    ON DUPLICATE KEY UPDATE player_uuid = ?;
        """.trimIndent()

        con.prepareStatement(sql).use { stmt ->
            stmt.setString(1, player.uniqueId.toString())
            stmt.setBigDecimal(2, initialBalance)
            stmt.setString(3, player.uniqueId.toString())
            stmt.executeQuery()
        }.close()

        con.close()
    }
}