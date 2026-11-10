package my.rinbridge

import java.util.Locale

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
        val text = input.lowercase(Locale.ROOT)
            .trim()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")

        return when {
            text.contains("buka musicolet") ||
            text.contains("open musicolet") ->
                RinCommand.OPEN_MUSICOLET

            text.contains("lagu seterusnya") ||
            text.contains("lagu selepas ini") ||
            text.contains("skip lagu") ||
            text.contains("next song") ||
            text == "next" ->
                RinCommand.NEXT

            text.contains("lagu sebelumnya") ||
            text.contains("lagu sebelum") ||
            text.contains("lagu lepas") ||
            text.contains("previous song") ||
            text == "previous" ->
                RinCommand.PREVIOUS

            text.contains("hentikan muzik") ||
            text.contains("henti muzik") ||
            text.contains("berhenti muzik") ||
            text.contains("stop music") ||
            text == "stop" ||
            text == "henti" ->
                RinCommand.STOP

            text.contains("berhenti sekejap") ||
            text.contains("jeda muzik") ||
            text.contains("pause music") ||
            text == "pause" ||
            text == "pause muzik" ->
                RinCommand.PAUSE

            text.contains("mainkan muzik") ||
            text.contains("main muzik") ||
            text.contains("main lagu") ||
            text.contains("mula muzik") ||
            text.contains("sambung muzik") ||
            text.contains("teruskan muzik") ||
            text.contains("play music") ||
            text == "play" ->
                RinCommand.PLAY

            else -> RinCommand.UNKNOWN
        }
    }
}