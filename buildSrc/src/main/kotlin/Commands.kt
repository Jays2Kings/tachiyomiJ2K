import org.gradle.api.Project
import java.text.SimpleDateFormat
import java.util.TimeZone
import java.util.Date

// Git is needed in your system PATH for these commands to work.
// If it's not installed, you can return a random value as a workaround
fun Project.getCommitCount(): String {
    return runCommand("git rev-list --count HEAD")
    // return "1"
}

fun Project.getBetaCount(): String {
    val betaTags = runCommand("git tag -l --sort=refname v${AndroidVersions.versionName}-b*")
    return String.format("%02d", if (betaTags.isNotEmpty()) {
        val betaTag = betaTags.split("\n").last().substringAfter("-b").toIntOrNull()
        ((betaTag ?: 0) + 1)
    } else {
        1
    })
    // return "1"
}


fun Project.getGitSha(): String {
    return runCommand("git rev-parse --short HEAD")
    // return "1"
}

fun Project.getBuildTime(): String {
    val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'")
    df.timeZone = TimeZone.getTimeZone("UTC")
    return df.format(Date())
}

fun Project.runCommand(command: String): String {
    return try {
        val process = ProcessBuilder(command.split(" "))
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        output.trim()
    } catch (e: Exception) {
        ""
    }
}
