package cc.ptoe.messenger.presentation.utils

/**
 * 剥离 AI 回复中的思考块（think/thinking 标签包裹的内容），用于聊天列表预览等纯文本场景。
 * 兼容带属性/大小写混合的标签、thinking 变体，以及流式中断导致的未闭合 think 块。
 * 使用 [\\s\\S] 代替 . 以可靠匹配跨行内容，不依赖 dotall 标志。
 */
fun stripThinkBlock(content: String): String {
    return content
        .replace(Regex("^<think(?:ing)?>[\\s\\S]*?</think(?:ing)?>"), "")
        .trim()
}

/**
 * 从 content 中提取首个 think 块的内部文本和剩余正文。
 * 用于将显示用的 `<think>` 标签还原为 `reasoning_content` 字段发送给 API。
 *
 * @return Pair(reasoningContent, remainingContent)
 */
fun extractThinkContent(content: String): Pair<String?, String> {
    val regex = Regex("^<think(?:ing)?>([\\s\\S]*?)</think(?:ing)?>\\s*")
    val match = regex.find(content)
    return if (match != null) {
        val reasoning = match.groupValues[1]
        val remaining = content.substring(match.range.last + 1).trimStart()
        Pair(reasoning, remaining)
    } else {
        Pair(null, content)
    }
}
