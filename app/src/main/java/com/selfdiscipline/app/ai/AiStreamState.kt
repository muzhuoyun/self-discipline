package com.selfdiscipline.app.ai

/** AI 流式请求的界面状态 */
sealed interface AiStreamState {
    data object Idle : AiStreamState
    data object Loading : AiStreamState
    data class Streaming(val text: String) : AiStreamState
    data class Done(val text: String) : AiStreamState
    data class Error(val message: String) : AiStreamState
}

/** 多轮对话中的一轮 */
data class ChatTurn(val role: String, val content: String) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}

/** AI 辅助判断的解析结果 */
data class AutoCheckOutcome(
    /** 逐项勾选建议：index -> 是否勾选 */
    val items: Map<Int, Boolean>,
    /** 逐项理由：index -> 理由 */
    val reasons: Map<Int, String>,
    /** 戒淫：建议等级（0/5/8/10），其他类为 null */
    val level: Int? = null,
    val levelReason: String? = null,
)
