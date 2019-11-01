package com.r3.corda.lib.chat.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.chat.contracts.states.ChatSessionInfo
import com.r3.corda.lib.chat.workflows.flows.internal.CloseMessagesFlow
import com.r3.corda.lib.chat.workflows.flows.internal.UpdateReceiversFlow
import com.r3.corda.lib.chat.workflows.flows.utils.chatVaultService
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap

@InitiatingFlow
@StartableByService
@StartableByRPC
class RemoveParticipantsFlow(
        private val chatId: UniqueIdentifier,
        private val toRemove: List<Party>
) : FlowLogic<StateAndRef<ChatSessionInfo>>() {
    @Suspendable
    override fun call(): StateAndRef<ChatSessionInfo> {
        val sessionStateRef = chatVaultService.getSessionInfoOrNull(chatId)
        require(sessionStateRef != null) { "ChatId must exist." }

        val session = sessionStateRef!!.state.data
        requireThat {
            "Only chat admin can remove participants from chat." using (ourIdentity == session.admin)
        }
        // close all messages from other sides
        toRemove.map { initiateFlow(it).send(chatId) }

        // update the receivers for remaining
        return subFlow(UpdateReceiversFlow(
                chatId = chatId,
                toRemove = toRemove
        ))
    }
}

@InitiatedBy(RemoveParticipantsFlow::class)
class RemoveParticipantsFlowResponder(private val otherSession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val chatId = otherSession.receive<UniqueIdentifier>().unwrap { it }
        return subFlow(CloseMessagesFlow(chatId))
    }
}
