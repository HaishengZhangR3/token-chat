package com.r3.corda.lib.chat.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.chat.workflows.flows.internal.CloseMessagesFlow
import com.r3.corda.lib.chat.workflows.flows.internal.CloseMetaInfoFlow
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
class CloseChatFlow(
        private val chatId: UniqueIdentifier
) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val metaInfoStateRef = chatVaultService.getMetaInfoOrNull(chatId)
        require(metaInfoStateRef != null) { "ChatId must exist." }

        val metaInfo = metaInfoStateRef!!.state.data
        requireThat {
            "Only chat admin can close chat." using (ourIdentity == metaInfo.admin)
        }

        // close all messages in our side
        subFlow(CloseMessagesFlow(chatId))

        // close all messages from other sides
        metaInfo.receivers.map { initiateFlow(it).send(chatId) }

        // close meta
        val txn = subFlow(CloseMetaInfoFlow(chatId))
        subFlow(ChatNotifyFlow(info = listOf(metaInfo), command = CloseCommand()))
        return txn
    }
}

@InitiatedBy(CloseChatFlow::class)
class CloseChatFlowResponder(private val otherSession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {

        val chatId = otherSession.receive<UniqueIdentifier>().unwrap { it }
        val metaInfo = chatVaultService.getMetaInfo(chatId).state.data

        val txn = subFlow(CloseMessagesFlow(chatId))
        subFlow(ChatNotifyFlow(info = listOf(metaInfo), command = CloseCommand()))
        return txn

    }
}
