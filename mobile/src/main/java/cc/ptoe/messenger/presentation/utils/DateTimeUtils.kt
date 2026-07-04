package cc.ptoe.messenger.presentation.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateTimeUtils {

    fun formatMessageTime(timestamp: Long): String {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            timeInMillis = timestamp
        }

        val isToday = now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)

        if (isToday) {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }

        val yesterday = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val isYesterday = yesterday.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                yesterday.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)

        if (isYesterday) {
            return "昨天"
        }

        val isThisYear = now.get(Calendar.YEAR) == target.get(Calendar.YEAR)

        return if (isThisYear) {
            val sdf = SimpleDateFormat("MM/dd", Locale.getDefault())
            sdf.format(Date(timestamp))
        } else {
            val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
