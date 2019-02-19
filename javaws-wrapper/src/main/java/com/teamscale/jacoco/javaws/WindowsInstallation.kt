package com.teamscale.jacoco.javaws

import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.apache.commons.exec.PumpStreamHandler
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*

/**
 * Creates the directory and its parent directories if they do not exist yet.
 * @return true if the directory was successfully created or already existed.
 */
private fun Path.ensureDirectoryExists(): Boolean = this.toFile().mkdirs() || this.toFile().isDirectory

/**
 * Installs/uninstalls the wrapper under Windows.
 *
 * We must set the file type association for JNLP files so it points to our
 * wrapper. We must also overwrite the system security policy since with some
 * JREs the additional system property the wrapper sets to make the JVM use our
 * custom security policy is ignored.
 */
class WindowsInstallation @Throws(InstallationException::class)
constructor(workingDirectory: Path) {

    private val systemSecurityPolicy: Path
    private val systemJavaws: Path
    private val wrapperPaths: WrapperPaths
    private val backupPaths: BackupPaths

    /** Checks whether the wrapper is currently installed.  */
    private val isInstalled: Boolean
        get() = Files.exists(backupPaths.ftypeMapping)

    init {
        this.systemSecurityPolicy = jvmInstallPath.resolve("lib/security").resolve(SECURITY_POLICY_FILE)
        this.systemJavaws = jvmInstallPath.resolve("bin/javaws")
        this.wrapperPaths = WrapperPaths(workingDirectory)
        this.backupPaths = BackupPaths(workingDirectory.resolve("backup"))
        validate()
    }

    @Throws(InstallationException::class)
    private fun validate() {
        if (!backupPaths.backupDirectory.ensureDirectoryExists()) {
            throw InstallationException("Cannot create backup directory at " + backupPaths.backupDirectory)
        }

        if (!Files.exists(systemSecurityPolicy)) {
            throw InstallationException(
                "Could not locate the javaws security policy file at "
                        + systemSecurityPolicy + ". Please make sure the JAVA_HOME environment variable is properly set"
            )
        }

        if (!Files.exists(wrapperPaths.securityPolicy) || !Files.exists(wrapperPaths.wrapperExecutable)) {
            throw InstallationException("Could not locate all necessary data files that came with this program." + " Please make sure you run this installation routine from within its installation directory.")
        }
    }

    /**
     * Installs the wrapper or throws an exception if the installation fails. In
     * case of an exception, the installation may be partly done.
     */
    @Throws(InstallationException::class)
    fun install() {
        if (isInstalled) {
            throw InstallationException("Wrapper is already installed")
        }

        if (!wrapperPaths.defaultOutputDirectory.ensureDirectoryExists()) {
            System.err.println(
                "Unable to create default output directory " + wrapperPaths.defaultOutputDirectory
                        + ". Please create it yourself"
            )
        }

        try {
            Files.copy(systemSecurityPolicy, backupPaths.securityPolicy, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            throw InstallationException(
                "Failed to backup current javaws security policy from "
                        + systemSecurityPolicy + " to " + backupPaths.securityPolicy, e
            )
        }

        try {
            backupPaths.ftypeMapping.toFile().writeText(readCurrentJnlpFtype())
        } catch (e: IOException) {
            throw InstallationException(
                "Failed to backup current file type mapping for JNLP files to " + backupPaths.ftypeMapping, e
            )
        } catch (e: InterruptedException) {
            throw InstallationException(
                "Failed to backup current file type mapping for JNLP files to " + backupPaths.ftypeMapping,
                e
            )
        }

        try {
            Files.copy(wrapperPaths.securityPolicy, systemSecurityPolicy, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            throw InstallationException(
                "Failed to overwrite the system security policy at " + systemSecurityPolicy
                        + ". You must run this installer as an administrator", e
            )
        }

        try {
            setFtype(JNLP_FTYPE + "=" + wrapperPaths.wrapperExecutable.toAbsolutePath() + " \"%1\"")
        } catch (e: IOException) {
            throw InstallationException(
                "Failed to change the file type mapping for JNLP files. You must run this installer as an administrator",
                e
            )
        } catch (e: InterruptedException) {
            throw InstallationException(
                "Failed to change the file type mapping for JNLP files. You must run this installer as an administrator",
                e
            )
        }

        val properties = Properties()
        properties.setProperty("javaws", systemJavaws.toAbsolutePath().toString())
        properties.setProperty(
            "agentArguments",
            "out=" + wrapperPaths.defaultOutputDirectory.toAbsolutePath() + ",includes=*com.yourcompany.*"
        )

        try {
            FileOutputStream(wrapperPaths.configProperties.toFile()).use { outputStream ->
                properties.store(outputStream, "")
            }
        } catch (e: IOException) {
            System.err.print(
                "WARN: Failed to write the wrapper config file to " + wrapperPaths.configProperties
                        + ". The installation itself was successful but you'll have to configure the wrapper manually (see the userguide for instructions)"
            )
            e.printStackTrace(System.err)
        }

        println("The installation was successful. Please fill in the agent arguments in the config file " + wrapperPaths.configProperties)
    }

    /**
     * Uninstalls the wrapper or throws an exception if the uninstallation fails. In
     * case of an exception, the uninstallation may be partly done.
     */
    @Throws(InstallationException::class)
    fun uninstall() {
        if (!isInstalled) {
            throw InstallationException("Wrapper does not seem to be installed")
        }

        val oldFtypeMapping: String
        try {
            oldFtypeMapping = backupPaths.ftypeMapping.toFile().readText()
        } catch (e: IOException) {
            throw InstallationException(
                "Failed to read the backup of the file type mapping for JNLP files from " + backupPaths.ftypeMapping,
                e
            )
        }

        try {
            setFtype(oldFtypeMapping)
        } catch (e: IOException) {
            throw InstallationException(
                "Failed to change the file type mapping for JNLP files. You must run this installer as an administrator",
                e
            )
        } catch (e: InterruptedException) {
            throw InstallationException(
                "Failed to change the file type mapping for JNLP files. You must run this installer as an administrator",
                e
            )
        }

        try {
            Files.copy(backupPaths.securityPolicy, systemSecurityPolicy, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            throw InstallationException(
                "Failed to overwrite the system security policy at " + systemSecurityPolicy
                        + " with the backup from " + backupPaths.securityPolicy
                        + ". You must run this installer as an administrator", e
            )
        }

        try {
            Files.deleteIfExists(backupPaths.ftypeMapping)
            Files.deleteIfExists(backupPaths.securityPolicy)
        } catch (e: IOException) {
            System.err.println(
                "WARN: Failed to delete the backup files. The uninstallation was successful but the backup files remain at " + backupPaths.backupDirectory
            )
            e.printStackTrace(System.err)
        }

    }

    @Throws(IOException::class, InterruptedException::class)
    private fun readCurrentJnlpFtype(): String {
        return runFtype(JNLP_FTYPE)
    }

    /** Sets the given mapping of the form `MAPPING=COMMAND`.  */
    @Throws(InstallationException::class, IOException::class, InterruptedException::class)
    private fun setFtype(desiredMapping: String) {
        val ftypeOutput = runFtype(desiredMapping)
        if (!readCurrentJnlpFtype().equals(desiredMapping, ignoreCase = true)) {
            throw InstallationException(
                "Failed to set file mapping $desiredMapping. Output of ftype: $ftypeOutput"
            )
        }
    }

    private inner class BackupPaths(internal val backupDirectory: Path) {
        internal val securityPolicy: Path = backupDirectory.resolve(SECURITY_POLICY_FILE)
        internal val ftypeMapping: Path = backupDirectory.resolve(FTYPE_MAPPING_BACKUP_FILE)
    }

    private inner class WrapperPaths(wrapperDirectory: Path) {
        internal val securityPolicy: Path = wrapperDirectory.resolve("agent.policy")
        internal val wrapperExecutable: Path = wrapperDirectory.resolve("bin/javaws")
        internal val configProperties: Path = wrapperDirectory.resolve("javaws.properties")
        internal val defaultOutputDirectory: Path = wrapperDirectory.resolve("coverage")
    }

    /** Thrown if the installation/uninstallation fails.  */
    class InstallationException(message: String, cause: Throwable? = null) : Exception(message, cause)

    companion object {

        private const val JNLP_FTYPE = "JNLPFile"
        private const val SECURITY_POLICY_FILE = "javaws.policy"
        private const val FTYPE_MAPPING_BACKUP_FILE = "ftype.bak"

        /** Runs the ftype shell builtin to change file associations.  */
        @Throws(IOException::class, InterruptedException::class)
        private fun runFtype(argument: String): String {
            val commandLine = CommandLine("cmd.exe")
            commandLine.addArgument("/s")
            commandLine.addArgument("/c")
            // cmd.exe rejects the command unless we disable quoting and wrap the entire
            // thing in quotes for the /s parameter's quote handling
            commandLine.addArgument("\"ftype $argument\"", false)

            val executor = DefaultExecutor()
            val outputStream = ByteArrayOutputStream()
            val streamHandler = PumpStreamHandler(outputStream)
            executor.streamHandler = streamHandler
            // ftype has weird exit values. We just ignore them and only check the output
            executor.setExitValues(null)

            executor.execute(commandLine)
            return outputStream.toString().trim { it <= ' ' }
        }

        /** The path where the currently running JVM is installed.  */
        private val jvmInstallPath: Path
            get() = Paths.get(System.getProperty("java.home"))
    }

}