package com.r3.demo.chatapi.observer

import co.paralleluniverse.fibers.Suspendable
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.r3.corda.lib.chat.contracts.states.ChatMessage
import com.r3.corda.lib.chat.contracts.states.ChatSessionInfo
import com.r3.corda.lib.chat.workflows.flows.observer.*
import com.r3.demo.chatapi.data.ChatMessageData
import com.r3.demo.chatapi.data.ChatSessionInfoData
import net.corda.core.contracts.ContractState
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.unwrap

@Suppress("UNCHECKED_CAST")
@InitiatedBy(ChatNotifyFlow::class)
class ChatObserverFlow(private val otherSession: FlowSession) : FlowLogic<Unit>() {

    companion object {
        private val log = contextLogger()
    }

    @Suspendable
    override fun call() {
        val (command, info) = otherSession.receive<List<Any>>().unwrap { it }

        println("${ourIdentity.name.organisation} got a notice from Chat SDK, ID: ${info}, cmd: $command")

        val data = parseData(command = command as NotifyCommand, info = info as List<ContractState>)
        if (data.isNotEmpty()) {
            wsService.wsServer.getNotifyList().map { it.send(data) }
        }
    }

    private fun parseData(command: NotifyCommand, info: List<ContractState>): String =
            when (command) {
                // is CreateSession: don't care, will update customers only after ChatMessage created
                is CreateCommand, is SendMessageCommand -> {
                    val message = info.single() as ChatMessage
                    chatMessageToJson(command, message)
                }
                is CloseCommand -> {
                    val session = info.single() as ChatSessionInfo
                    sessionInfoToJson(command, session)
                }
                is AddParticipantsCommand -> {
                    val session = info.single() as ChatSessionInfo
                    sessionInfoToJson(command, session)
                }
                is RemoveParticipantsCommand -> {
                    val session = info.single() as ChatSessionInfo
                    sessionInfoToJson(command, session)
                }
                else -> ""
            }
    private fun chatMessageToJson(command: NotifyCommand, message: ChatMessage): String {
        val mapper = jacksonObjectMapper()
        return mapper.writeValueAsString(listOf(commandToString(command), ChatMessageData.fromState(message)))
    }

    private fun sessionInfoToJson(command: NotifyCommand, session: ChatSessionInfo): String {
        val mapper = jacksonObjectMapper()
        return mapper.writeValueAsString(listOf(commandToString(command), ChatSessionInfoData.fromState(session)))
    }

    private fun commandToString(command: NotifyCommand): String =
        when (command) {
            is  CreateCommand              -> "CreateCommand"
            is  SendMessageCommand         -> "SendMessageCommand"
            is  CloseCommand               -> "CloseCommand"
            is  AddParticipantsCommand     -> "AddParticipantsCommand"
            is  RemoveParticipantsCommand  -> "RemoveParticipantsCommand"
            else                           -> ""
        }

    private fun chatMessageToJson(message: ChatMessage) =
            """
                    ChatId: ${message.linearId},
                    Sender: ${message.sender.name.organisation},
                    Content: ${message.content}
                """.trimIndent()

}
