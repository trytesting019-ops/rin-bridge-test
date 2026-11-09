
package my.rinbridge

enum class RinCommand {
    PLAY,
    PAUSE,
    STOP,
    NEXT,
    PREVIOUS,
    OPEN_MUSICOLET,
    UNKNOWN
}

object CommandRouter {

    fun parse(input: String): RinCommand {

        val text = input.lowercase()
            .trim()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")

        return when {

            text.contains("next song") ||
            text.contains("lagu seterusnya") ||
            text == "next" ->
                RinCommand.NEXT

            text.contains("previous song") ||
            text.contains("lagu sebelum") ||
            text == "previous" ->
                RinCommand.PREVIOUS

            text.contains("buka musicolet") ||
            text.contains("open musicolet") ->
                RinCommand.OPEN_MUSICOLET

            text.contains("stop music") ||
            text.contains("hentikan muzik") ||
            text == "stop" ->
                RinCommand.STOP

            text.contains("pause") ||
            text.contains("berhenti sekejap") ->
                RinCommand.PAUSE

            text.contains("play") ||
            text.contains("mainkan muzik") ||
            text.contains("mula muzik") ->
                RinCommand.PLAY

            else -> RinCommand.UNKNOWN
        }
    }
}
