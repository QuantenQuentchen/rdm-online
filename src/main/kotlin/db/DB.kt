package org.rdm.online.db

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.sqlite.Function
import java.sql.Connection
import java.sql.DriverManager
import java.text.Normalizer

object DB {
    private var initializedPath: String? = null

    fun initialize(databasePath: String = Config.DB_PATH) {
        if (initializedPath == databasePath) return

        println("Initializing database at $databasePath")

        val db = Database.connect(
            getNewConnection = {
                DriverManager
                    .getConnection("jdbc:sqlite:$databasePath")
                    .also { registerUnicodeFunctions(it) }
            }
        )

        TransactionManager.managerFor(db).newTransaction().let { tx ->
            tx.connection.autoCommit = true
            tx.exec("PRAGMA journal_mode=WAL;")
            //tx.exec("PRAGMA synchronous=NORMAL;")
            //tx.exec("PRAGMA foreign_keys=ON;")
            tx.connection.autoCommit = false
            tx.commit()
            tx.close()
        }

        transaction(db) {
            SchemaUtils.create(
                FileDB.Files,

                GlobData.GlobalData,
                GlobData.GlobalAncillary,

                PresetDB.Presets,
                PresetDB.ColorMaps,
                PresetDB.Equations,
                PresetDB.Slices,

                Solution.Solutions,

                SolutionSession.SolutionSessions,
                SolutionSession.RenderCommand,
                SolutionSession.CommandQueue,
                SolutionSession.RenderQueue,

                Tags.Tags,
                Tags.SolutionTags,
                Tags.PresetTags,

                User.Users,
                User.SessionAccess,
                User.UserSettings,

                SettingsDB.AutoPresets,
                MetaDB.Initialized
            )
        }
        initializedPath = databasePath
        if (!MetaDB.wasStartedUp()) onFirstStartup()
    }

    private fun onFirstStartup() {
        corneringTagId = Tags.getOrCreateTag("Cornering")
        DefaultColorMaps.registerPredefinedColorMaps()
        MetaDB.setStartupSucceeded(true)
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