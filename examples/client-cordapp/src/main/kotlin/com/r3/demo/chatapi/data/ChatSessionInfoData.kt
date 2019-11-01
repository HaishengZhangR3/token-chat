package com.r3.demo.chatapi.data

import com.r3.corda.lib.chat.contracts.ChatSessionInfoContract
import com.r3.corda.lib.chat.contracts.states.ChatSessionInfo
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.UniqueIdentifier
import java.time.Instant

@BelongsToContract(ChatSessionInfoContract::class)
data class ChatSessionInfoData(
        val linearId: UniqueIdentifier,
        val created: Instant,
        val participants: List<String>,
        val admin: String,
        val receivers: List<String>,
        val subject: String,
        val status: String
){
    companion object {
        fun fromState(chatSessionInfo: ChatSessionInfo): ChatSessionInfoData =
                ChatSessionInfoData(
                        linearId = chatSessionInfo.linearId,
                        created = chatSessionInfo.created,
                        participants = chatSessionInfo.participants.map { it.nameOrNull()!!.organisation },
                        admin = chatSessionInfo.admin.name.organisation,
                        receivers = chatSessionInfo.receivers.map { it.nameOrNull().organisation },
                        subject = chatSessionInfo.subject,
                        status = chatSessionInfo.status.toString()
                )
    }
}
