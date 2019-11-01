package com.r3.corda.lib.chat.workflows.flows.internal

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.chat.contracts.states.ChatSessionInfo
import com.r3.corda.lib.chat.contracts.states.ChatStatus
import com.r3.corda.lib.chat.workflows.flows.utils.chatVaultService
import com.r3.corda.lib.tokens.contracts.utilities.withNotary
import com.r3.corda.lib.tokens.workflows.flows.rpc.CreateEvolvableTokens
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StartableByService
import net.corda.core.identity.Party
import java.util.*

@InitiatingFlow
@StartableByService
@StartableByRPC
class CreateSessionInfoFlow(
        private val chatId: UniqueIdentifier = UniqueIdentifier.fromString(UUID.randomUUID().toString()),
        private val subject: String,
        private val receivers: List<Party>
) : FlowLogic<StateAndRef<ChatSessionInfo>>() {

    @Suspendable
    override fun call(): StateAndRef<ChatSessionInfo> {
        val notary = chatVaultService.notary()
        val session = ChatSessionInfo(
                linearId = chatId,
                admin = ourIdentity,
                receivers = receivers,
                subject = subject,
                status = ChatStatus.ACTIVE
        )
        val transactionState = session withNotary notary
        val signedTxn = subFlow(CreateEvolvableTokens(transactionState))

        return signedTxn.coreTransaction.outRefsOfType<ChatSessionInfo>().single()
    }
}
