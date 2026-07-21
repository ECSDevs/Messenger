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
