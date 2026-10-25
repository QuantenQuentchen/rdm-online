package org.rdm.online

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.rdm.online.api.net.mainModule

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main() {
    Logging.configure(
        LoggingConfig(
            errorsToStderr = System.getenv("LOG_ERRORS_TO_STDERR")?.toBooleanStrictOrNull() ?: true,
        ),
    )

    val port = 8080

    println("Serving on http://localhost:$port")
    embeddedServer(
        Netty,
        port = port,
        host = "0.0.0.0",
        module = Application::mainModule
    ).start(wait = true)
}