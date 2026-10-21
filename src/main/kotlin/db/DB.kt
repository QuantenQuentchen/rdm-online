package org.rdm.online.db

import org.rdm.online.db.auth.AuthDB
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.rdm.online.db.user.UserDB
import org.rdm.online.db.funding.FundableDB
import org.sqlite.Function
import org.rdm.online.db.awards.AwardsDB
import java.sql.Connection
import java.sql.DriverManager
import java.text.Normalizer

object DB {
    private var initializedPath: String? = null


    // sqlite-jdbc applies these on every connection. They replace the Go connection hook
    // (foreign_keys, busy_timeout, synchronous), the WAL pragma and `_txlock=immediate`.
    // This is the only SQLite-specific line: swap URL + driver for Postgres and drop the params.
    private const val URL =
        "jdbc:sqlite:data.db?journal_mode=WAL&busy_timeout=5000&synchronous=NORMAL" +
                "&foreign_keys=true&transaction_mode=IMMEDIATE"

    fun initialize(databasePath: String = "data.db") {
        if (initializedPath == databasePath) return

        println("Initializing database at $databasePath")

        val db = Database.connect(
            getNewConnection = {
                DriverManager
                    .getConnection("jdbc:sqlite:$databasePath")
                    .also {
                        registerUnicodeFunctions(it)
                        it.createStatement().use { statement ->
                            statement.execute("PRAGMA foreign_keys=ON")
                        }
                    }
            }
        )

        TransactionManager.managerFor(db).newTransaction().let { tx ->
            tx.connection.autoCommit = true
            tx.exec("PRAGMA journal_mode=WAL;")
            //tx.exec("PRAGMA synchronous=NORMAL;")
            tx.connection.autoCommit = false
            tx.commit()
            tx.close()
        }

        transaction(db) {
            SchemaUtils.create(
                UserDB.Users,

                AuthDB.Sessions,

                AwardsDB.Categories,
                AwardsDB.Suggestions,

                FundableDB.PaymentSchedules,
                FundableDB.Fundables,
                FundableDB.Components,
                FundableDB.ComponentInterests,
                FundableDB.ComponentCommitments,
                FundableDB.Categories,
                FundableDB.ToCategories
            )
        }
        initializedPath = databasePath
    }

    private fun registerUnicodeFunctions(connection: Connection) {
        org.sqlite.Function.create(
            connection,
            "unicode_lower",
            object : org.sqlite.Function() {
                override fun xFunc() {
                    val input = value_text(0)

                    if (input == null) {
                        result()
                        return
                    }

                    result(
                        Normalizer.normalize(input, Normalizer.Form.NFC)
                            .lowercase()
                    )
                }
            },
            1,
            Function.FLAG_DETERMINISTIC
        )
    }
}