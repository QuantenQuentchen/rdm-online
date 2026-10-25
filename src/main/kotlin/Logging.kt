package org.rdm.online

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.filter.ThresholdFilter
import ch.qos.logback.core.ConsoleAppender
import org.slf4j.LoggerFactory

data class LoggingConfig(
    val errorsToStderr: Boolean = true,
)

object Logging {
    fun configure(config: LoggingConfig) {
        val context = LoggerFactory.getILoggerFactory() as? LoggerContext
            ?: return

        val root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
        root.detachAndStopAllAppenders()
        root.level = Level.INFO

        val encoder = PatternLayoutEncoder().apply {
            this.context = context
            pattern = "%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n"
            start()
        }

        root.addAppender(consoleAppender(context, encoder, "stdout", "System.out"))
        if (config.errorsToStderr) {
            root.addAppender(
                consoleAppender(
                    context = context,
                    encoder = encoder,
                    name = "stderr",
                    target = "System.err",
                    minimumLevel = Level.ERROR,
                ),
            )
        }
    }

    private fun consoleAppender(
        context: LoggerContext,
        encoder: PatternLayoutEncoder,
        name: String,
        target: String,
        minimumLevel: Level? = null,
    ): ConsoleAppender<ch.qos.logback.classic.spi.ILoggingEvent> =
        ConsoleAppender<ch.qos.logback.classic.spi.ILoggingEvent>().apply {
            this.context = context
            this.name = name
            this.encoder = encoder
            this.target = target
            minimumLevel?.let {
                addFilter(ThresholdFilter().apply {
                    this.context = context
                    setLevel(it.levelStr)
                    start()
                })
            }
            start()
        }
}
