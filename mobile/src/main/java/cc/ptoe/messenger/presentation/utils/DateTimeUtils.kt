package cc.ptoe.messenger.presentation.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateTimeUtils {

    fun formatMessageTime(timestamp: Long): String {
        // 消息气泡左下角始终显示时间（HH:mm），具体日期由日期分隔符横幅展示
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun isSameDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return isSameDay(ca, cb)
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }

    /**
     * 用于消息列表日期分隔符显示：今天 / 昨天 / 具体日期
     */
    fun formatDateSeparator(timestamp: Long): String {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = timestamp }

        if (isSameDay(now, target)) return "今天"

        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        if (isSameDay(yesterday, target)) return "昨天"

        val isThisYear = now.get(Calendar.YEAR) == target.get(Calendar.YEAR)
        val pattern = if (isThisYear) "M月d日" else "yyyy年M月d日"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
    }
}
