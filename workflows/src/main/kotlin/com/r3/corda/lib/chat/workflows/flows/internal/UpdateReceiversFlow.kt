package com.r3.corda.lib.chat.workflows.flows.internal

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.chat.contracts.states.ChatSessionInfo
import com.r3.corda.lib.chat.workflows.flows.observer.AddParticipantsCommand
import com.r3.corda.lib.chat.workflows.flows.observer.ChatNotifyFlow
import com.r3.corda.lib.chat.workflows.flows.observer.NotifyCommand
import com.r3.corda.lib.chat.workflows.flows.observer.RemoveParticipantsCommand
import com.r3.corda.lib.chat.workflows.flows.utils.chatVaultService
import com.r3.corda.lib.tokens.workflows.flows.rpc.UpdateEvolvableToken
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.utilities.unwrap
import java.time.Instant

@InitiatingFlow
@StartableByService
@StartableByRPC
class UpdateReceiversFlow(
        private val chatId: UniqueIdentifier,
        private val toAdd: List<Party> = emptyList(),
        private val toRemove: List<Party> = emptyList()
) : FlowLogic<StateAndRef<ChatSessionInfo>>() {

    @Suspendable
    override fun call(): StateAndRef<ChatSessionInfo> {

        val sessionStateRef = chatVaultService.getSessionInfo(chatId)
        val session = sessionStateRef.state.data

        val newReceivers = session.receivers + toAdd - toRemove
        val newSession = session.copy(
                created = Instant.now(),
                receivers = newReceivers
        )

        val signedTxn = subFlow(UpdateEvolvableToken(sessionStateRef, newSession))
        val newSessionStateRef = signedTxn.coreTransaction.outRefsOfType<ChatSessionInfo>().single()

        // tell all participants to notify
        (session.receivers + session.admin + toAdd).map { initiateFlow(it).send(listOf(
                when {
                    toAdd.isNotEmpty()      -> AddParticipantsCommand()
                    toRemove.isNotEmpty()   -> RemoveParticipantsCommand()
                    else                    -> AddParticipantsCommand()
                },
                newSessionStateRef.state.data)) }

        return newSessionStateRef
    }
}

@InitiatedBy(UpdateReceiversFlow::class)
class UpdateReceiversFlowResponder(private val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val (command, session ) = otherSession.receive<List<Any>>().unwrap { it }
        subFlow(ChatNotifyFlow(info = listOf(session as ChatSessionInfo), command = command as NotifyCommand))
    }
}
