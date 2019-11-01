package com.r3.corda.lib.chat.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.chat.workflows.flows.internal.CloseMessagesFlow
import com.r3.corda.lib.chat.workflows.flows.internal.CloseSessionInfoFlow
import com.r3.corda.lib.chat.workflows.flows.observer.ChatNotifyFlow
import com.r3.corda.lib.chat.workflows.flows.observer.CloseCommand
import com.r3.corda.lib.chat.workflows.flows.utils.chatVaultService
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap

@InitiatingFlow
@StartableByService
@StartableByRPC
class CloseSessionFlow(
        private val chatId: UniqueIdentifier
) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val sessionStateRef = chatVaultService.getSessionInfoOrNull(chatId)
        require(sessionStateRef != null) { "ChatId must exist." }

        val session = sessionStateRef!!.state.data
        requireThat {
            "Only chat admin can close chat." using (ourIdentity == session.admin)
        }

        // close all messages in our side
        subFlow(CloseMessagesFlow(chatId))

        // close all messages from other sides
        session.receivers.map { initiateFlow(it).send(chatId) }

        // close session
        val txn = subFlow(CloseSessionInfoFlow(chatId))
        subFlow(ChatNotifyFlow(info = listOf(session), command = CloseCommand()))
        return txn
    }
}

@InitiatedBy(CloseSessionFlow::class)
class CloseSessionFlowResponder(private val otherSession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {

        val chatId = otherSession.receive<UniqueIdentifier>().unwrap { it }
        val session = chatVaultService.getSessionInfo(chatId).state.data

        val txn = subFlow(CloseMessagesFlow(chatId))
        subFlow(ChatNotifyFlow(info = listOf(session), command = CloseCommand()))
        return txn

    }
}
