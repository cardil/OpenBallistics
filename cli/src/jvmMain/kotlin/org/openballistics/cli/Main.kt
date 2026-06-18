package org.openballistics.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands

class OpenBallisticsCommand : CliktCommand(name = "openballistics") {
    override fun run() = Unit
}

fun main(args: Array<String>) {
    OpenBallisticsCommand()
        .subcommands(
            SolveCommand(),
            RangeCardCommand(),
            ProfilesCommand(),
            InitCommand()
        )
        .main(args)
}
