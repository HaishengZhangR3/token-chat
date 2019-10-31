package com.r3.corda.lib.chat.workflows.flows.internal

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.chat.contracts.states.ChatMetaInfo
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

@InitiatingFlow
@StartableByService
@StartableByRPC
class UpdateReceiversFlow(
        private val chatId: UniqueIdentifier,
        private val toAdd: List<Party> = emptyList(),
        private val toRemove: List<Party> = emptyList()
) : FlowLogic<StateAndRef<ChatMetaInfo>>() {

    @Suspendable
    override fun call(): StateAndRef<ChatMetaInfo> {

        val metaInfoStateRef = chatVaultService.getMetaInfo(chatId)
        val metaInfo = metaInfoStateRef.state.data

        val newReceivers = metaInfo.receivers + toAdd - toRemove
        val newMetaInfo = metaInfo.copy(
                receivers = newReceivers
        )

        val signedTxn = subFlow(UpdateEvolvableToken(metaInfoStateRef, newMetaInfo))
        val newMetaStateRef = signedTxn.coreTransaction.outRefsOfType<ChatMetaInfo>().single()

        // tell all participants to notify
        (metaInfo.receivers + metaInfo.admin + toAdd).map { initiateFlow(it).send(listOf(
                when {
                    toAdd.isNotEmpty()      -> AddParticipantsCommand()
                    toRemove.isNotEmpty()   -> RemoveParticipantsCommand()
                    else                    -> AddParticipantsCommand()
                },
                newMetaStateRef.state.data)) }

        return newMetaStateRef
    }
}

@InitiatedBy(UpdateReceiversFlow::class)
class UpdateReceiversFlowResponder(private val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val (command, chatMetaInfo ) = otherSession.receive<List<Any>>().unwrap { it }
        subFlow(ChatNotifyFlow(info = listOf(chatMetaInfo as ChatMetaInfo), command = command as NotifyCommand))
    }
}
