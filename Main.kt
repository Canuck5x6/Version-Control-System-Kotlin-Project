package svcs

import java.io.File
import java.security.MessageDigest
import java.time.LocalDateTime

val LINE_SEP: String = System.getProperty("line.separator")
val PATH_SEP: String = File.separator

val VCS_FOLDER = ".${PATH_SEP}vcs"
val COMMIT_FOLDER = "$VCS_FOLDER${PATH_SEP}commits"
const val CONFIG_FILE = "config.txt"
const val INDEX_FILE = "index.txt"
const val LOG_FILE = "log.txt"

fun main(args: Array<String>) {

    if(args.isEmpty()) { showTheHelp(); return }

    createFolderIfNotExist(VCS_FOLDER)
    createFolderIfNotExist(COMMIT_FOLDER)

    doCommands(args[0], if(args.size > 1) args[1] else "")
}

fun doCommands(command:String, parameter:String)
{
    when(command){
        "config" -> handleConfig(parameter)
        "add" -> handleAdd(parameter)
        "log" -> handleLog()
        "commit" -> handleCommit(parameter)
        "checkout" -> handleCheckout(parameter)
        "--help" -> showTheHelp()
        else -> println("\'${command}\' is not a SVCS command.")
    }
}
// ************************************************
//
// Methods that handle command line options
//
// ************************************************
fun handleLog(){
    val logContents = getLogContents()
    if(logContents.size == 0) {
        println("No commits yet.")
    } else {
        for (logLine in logContents.reversed()) {
            val logDetails = logLine.split(",")
            println("commit ${logDetails[0]}")
            println("Author: ${logDetails[1]}")
            println("${logDetails[2]}${LINE_SEP}")
        }
    }
}

fun handleCommit(commitParameter: String = "") {
    when {
        commitParameter.isEmpty() -> println("Message was not passed.")
        getListOfChangedFiles().isEmpty() -> println("Nothing to commit.")
        else -> doCommit(commitParameter)
    }
}

fun getListOfChangedFiles():MutableList<String> {
    // Guard against nothing being tracked
    val listOfTrackedFiles = getListOfTrackedFiles()

    val logContent = getLogContents()

    return if (logContent.isEmpty()) listOfTrackedFiles else {
        val lastCommitId = logContent.last().split(",").first()
        val newListOfChangedFiles = mutableListOf<String>()

        for (aTrackedFile in listOfTrackedFiles) {
            // check if file is changed
            val curHash = getHashOfFile(aTrackedFile)
            val lastHash = getHashOfFile(aTrackedFile, "$COMMIT_FOLDER$PATH_SEP$lastCommitId$PATH_SEP")
            if (curHash != lastHash) {
                newListOfChangedFiles += aTrackedFile
            }
        }
        newListOfChangedFiles
    }
}

fun doCommit(commitNote: String) {
    val commitId = createCommitId()
    copyFilesToCommitFolder(commitId)

    // Append a line to log.txt
    val logFile = File("$VCS_FOLDER$PATH_SEP$LOG_FILE")
    logFile.appendText("$commitId,${getUserName()},$commitNote$LINE_SEP")

    println("Changes are committed.")
}

fun handleCheckout(checkoutParameter: String = ""){
    println(
        when {
            checkoutParameter.isEmpty() -> "Commit id was not passed."
            getMapOfCommits().contains(checkoutParameter) -> {
                copyFilesFromCommitFolder(checkoutParameter)
                "Switched to commit $checkoutParameter."
            }
            else -> "Commit does not exist."
        }
    )
}

fun handleConfig(userNameParameter: String = "") {
    val configFile = File("$VCS_FOLDER$PATH_SEP$CONFIG_FILE")

    if(userNameParameter.isEmpty()) {
        if(!configFile.exists()) {
            println("Please, tell me who you are.")
            return
        }
    }
    else {
        configFile.writeText(userNameParameter)
    }
    println("The username is ${getUserName(configFile)}.")
}

fun handleAdd(addParameter: String = ""){
    val indexFile = File("$VCS_FOLDER/$INDEX_FILE")

    if(addParameter.isEmpty()) {
        println(
            if(indexFile.exists()) {
                val configContent = getLinesOfFileAsList(indexFile)
                "Tracked files:$LINE_SEP${configContent.joinToString(LINE_SEP)}"
            } else {
                "Add a file to the index."
            }
        )
    }
    else {
        if(File(addParameter).exists()) {
            indexFile.appendText("$addParameter$LINE_SEP")
            println("The file \'$addParameter\' is tracked.")
        }else {
            println("Can't find '$addParameter'.")
        }
    }
}

fun showTheHelp(){
    println("""
        These are SVCS commands:
        config      Get and set a username.
        add         Add a file to the index.
        log         Show commit logs.
        commit      Save changes.
        checkout    Restore a file.
        """.trimIndent()
    )
}

// Utility Functions
fun getLinesOfFileAsList(fileToLoad: File):MutableList<String> {
    val listOfLines = if(fileToLoad.exists()) fileToLoad.readText().split(LINE_SEP).toMutableList() else mutableListOf()
    listOfLines.removeAll { it == "" }
    return listOfLines
}

fun getUserName(configFile: File = File("$VCS_FOLDER$PATH_SEP$CONFIG_FILE")): String {
    return configFile.readText()
}

fun createFolderIfNotExist(folderName: String){
    val vcsFolder = File(folderName)

    if(!vcsFolder.exists()) {
        vcsFolder.mkdir()
    }
}

fun createCommitId():String {
    // use the current date/time to define a commit id
    val sha = MessageDigest.getInstance("SHA-256")
    sha.update(LocalDateTime.now().hashCode().toByte())
    return hashBytes("SHA-256", sha.digest())
}

fun getListOfTrackedFiles(): MutableList<String> {
    val indexFile = File("$VCS_FOLDER$PATH_SEP$INDEX_FILE")

    return getLinesOfFileAsList(indexFile)
}

fun getLogContents(): MutableList<String> {
    val logFile = File("$VCS_FOLDER$PATH_SEP$LOG_FILE")

    return getLinesOfFileAsList(logFile)
}

fun getMapOfCommits(): Map<String, String> {
    val logFile = File("$VCS_FOLDER$PATH_SEP$LOG_FILE")
    return getLinesOfFileAsList(logFile).associateBy { it.split(",").first() }
}

fun getHashOfFile( aFile:String, path:String = "./" ): String {
    if (aFile.isNotEmpty()) {
        val fileContent = File("$path$aFile").readBytes()
        return hashBytes("SHA-256", fileContent)
    }

    return "Error"
}

/**
 * Supported algorithms on Android:
 *
 * Algorithm	Supported API Levels
 * MD5          1+
 * SHA-1	    1+
 * SHA-224	    1-8,22+
 * SHA-256	    1+
 * SHA-384	    1+
 * SHA-512	    1+
 */
const val HEX_CHARS = "0123456789ABCDEF"
fun hashString(type: String, input: String): String = hashBytes(type, input.toByteArray())

fun hashBytes(type: String, input: ByteArray): String {
    try {
        val bytes = MessageDigest
            .getInstance(type)
            .digest(input)
        return bytesToString(bytes)
    } catch (cnse: CloneNotSupportedException) {
        // do something else, such as the code shown below
        println("Note Cloneable: ${cnse.message}")
    } catch (e: Exception) {
        println("Exception happened:${e.message} so there!")
    }

    return "Error"
}

fun bytesToString(bytes: ByteArray): String {
    val result = StringBuilder(bytes.size * 2)

    bytes.forEach {
        val i = it.toInt()
        result.append(HEX_CHARS[i shr 4 and 0x0f])
        result.append(HEX_CHARS[i and 0x0f])
    }

    return result.toString()
}

fun copyFilesToCommitFolder(commitId:String ) {
    // Create folder with ID name in /vcs/commits
    val pathToCommitFolder = "$COMMIT_FOLDER$PATH_SEP$commitId"
    createFolderIfNotExist(pathToCommitFolder)

    val fileList = getListOfTrackedFiles()

    try {
        for (aFile in fileList) {
            if(aFile.isEmpty()) continue
            File(aFile).copyTo(File(pathToCommitFolder + PATH_SEP + aFile))
        }
    } catch (e: Exception) {
        println("Exception Caught:${e.message}")
    }
}

fun copyFilesFromCommitFolder(commitId:String ) {
    // Create folder with ID name in /vcs/commits
    val pathToCommitFolder = "$COMMIT_FOLDER$PATH_SEP$commitId"

    val filesToCopy = File(pathToCommitFolder).listFiles()
    if(filesToCopy != null)
    {
        try {
            for (aFile in filesToCopy) {
                aFile.copyTo(File(".$PATH_SEP${aFile.name}"), true)
            }
        } catch (e: Exception) {
            println("Exception Caught:${e.message}")
        }
    }
}