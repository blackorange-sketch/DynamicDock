package com.dynamicdock

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DockLogger {

    private const val FILE_NAME = "dynamicdock.log"
    private const val MAX_LOG_SIZE = 1024 * 1024

    @Synchronized
    fun log(context: Context, message: String) {
        try {
            val file = context.getFileStreamPath(FILE_NAME)

            if (file.exists() && file.length() > MAX_LOG_SIZE) {
                file.writeText("")
            }

            val time = SimpleDateFormat(
                "HH:mm:ss.SSS",
                Locale.US
            ).format(Date())

            file.appendText(
                "$time  $message\n"
            )
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun read(context: Context): String {
        return try {
            val file = context.getFileStreamPath(FILE_NAME)

            if (file.exists()) {
                file.readText()
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    @Synchronized
    fun clear(context: Context) {
        try {
            context.getFileStreamPath(FILE_NAME).writeText("")
        } catch (_: Exception) {
        }
    }
}
