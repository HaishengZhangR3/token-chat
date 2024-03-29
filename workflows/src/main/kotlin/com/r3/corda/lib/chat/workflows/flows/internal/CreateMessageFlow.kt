package com.r3.corda.lib.chat.workflows.flows.internal

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.chat.contracts.states.ChatMessage
import com.r3.corda.lib.chat.contracts.states.ChatSessionInfo
import com.r3.corda.lib.chat.workflows.flows.utils.chatVaultService
import com.r3.corda.lib.chat.workflows.flows.utils.randomID
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.workflows.flows.issue.IssueTokensFlow
import com.r3.corda.lib.tokens.workflows.flows.issue.IssueTokensFlowHandler
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*

@InitiatingFlow
@StartableByService
@StartableByRPC
class CreateMessageFlow(
        private val chatId: UniqueIdentifier,
        private val content: String
) : FlowLogic<StateAndRef<ChatMessage>>() {

    @Suspendable
    override fun call(): StateAndRef<ChatMessage> {
        val session = chatVaultService.getSessionInfo(chatId).state.data

        val chatPointer = session.toPointer<ChatSessionInfo>()
        val issuedTokenType = chatPointer issuedBy ourIdentity

        val allReceivers = session.receivers + session.admin

        val messageId = randomID()
        val flows = allReceivers.map { receiver ->
            val message = ChatMessage(
                    token = issuedTokenType,
                    linearId = messageId,
                    content = content,
                    sender = ourIdentity,
                    holder = receiver
            )
            val initFlow = initiateFlow(receiver)
            val flow = IssueTokensFlow(message, listOf(initFlow), emptyList())
            subFlow(flow)
        }

        return flows.first().coreTransaction.outRefsOfType<ChatMessage>().single()
    }
}

@Suppress("unused")
@InitiatedBy(CreateMessageFlow::class)
class CreateMessageFlowResponse(private val flowSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(IssueTokensFlowHandler(flowSession))
    }
}
