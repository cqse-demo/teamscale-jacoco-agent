package com.teamscale.jacoco.agent

import com.beust.jcommander.JCommander
import com.beust.jcommander.JCommander.Builder
import com.beust.jcommander.Parameter
import com.beust.jcommander.ParameterException
import com.teamscale.jacoco.agent.commandline.Validator
import com.teamscale.jacoco.agent.convert.ConvertCommand
import com.teamscale.jacoco.util.LoggingUtils
import org.conqat.lib.commons.string.StringUtils
import org.jacoco.core.JaCoCo
import org.slf4j.Logger

import java.util.ResourceBundle

/** Provides a command line interface for interacting with JaCoCo.  */
class Main {

    /** The logger.  */
    private val logger = LoggingUtils.getLogger(this)

    /** The default arguments that will always be parsed.  */
    private val defaultArguments = DefaultArguments()

    /** The arguments for the one-time conversion process.  */
    private val command = ConvertCommand()

    /**
     * Parses the given command line arguments. Exits the program or throws an
     * exception if the arguments are not valid. Then runs the specified command.
     */
    @Throws(Exception::class)
    private fun parseCommandLineAndRun(args: Array<String>) {
        val builder = createJCommanderBuilder()
        val jCommander = builder.build()

        try {
            jCommander.parse(*args)
        } catch (e: ParameterException) {
            handleInvalidCommandLine(jCommander, e.message)
        }

        if (defaultArguments.help) {
            println("CQSE JaCoCo agent " + VERSION + " compiled against JaCoCo " + JaCoCo.VERSION)
            jCommander.usage()
            return
        }

        val validator = command.validate()
        if (!validator.isValid) {
            handleInvalidCommandLine(jCommander, StringUtils.CR + validator.errorMessage)
        }

        logger.info("Starting CQSE JaCoCo agent " + VERSION + " compiled against JaCoCo " + JaCoCo.VERSION)
        command.run()
    }

    /** Creates a builder for a [JCommander] object.  */
    private fun createJCommanderBuilder(): Builder {
        return JCommander.newBuilder().programName(Main::class.java.name).addObject(defaultArguments).addObject(command)
    }

    /** Default arguments that may always be provided.  */
    private class DefaultArguments {

        /** Shows the help message.  */
        @Parameter(names = arrayOf("--help"), help = true, description = "Shows all available command line arguments.")
        val help: Boolean = false

    }

    companion object {

        /** Version of this program.  */
        private val VERSION: String

        init {
            val bundle = ResourceBundle.getBundle("com.teamscale.jacoco.agent.app")
            VERSION = bundle.getString("version")
        }

        /** Entry point.  */
        @Throws(Exception::class)
        @JvmStatic
        fun main(args: Array<String>) {
            Main().parseCommandLineAndRun(args)
        }

        /** Shows an informative error and help message. Then exits the program.  */
        private fun handleInvalidCommandLine(jCommander: JCommander, message: String?) {
            System.err.println("Invalid command line: " + message + StringUtils.CR)
            jCommander.usage()
            System.exit(1)
        }
    }

}