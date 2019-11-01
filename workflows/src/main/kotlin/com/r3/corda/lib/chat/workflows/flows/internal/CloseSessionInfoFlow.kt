package com.r3.corda.lib.chat.workflows.flows.internal

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.chat.contracts.commands.CloseSession
import com.r3.corda.lib.chat.workflows.flows.utils.chatVaultService
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

@InitiatingFlow
@StartableByService
@StartableByRPC
class CloseSessionInfoFlow(
        private val chatId: UniqueIdentifier
) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {

        // get and consume all messages in vault
        val sessionStateAndRef = chatVaultService.getSessionInfo(chatId)
        val session = sessionStateAndRef.state.data

        val txnBuilder = TransactionBuilder(notary = sessionStateAndRef.state.notary)
                .addCommand(CloseSession(), session.participants.map { it.owningKey })
                .addInputState(sessionStateAndRef)
                .also { it.verify(serviceHub) }

        // sign it
        val selfSignedTxn = serviceHub.signInitialTransaction(txnBuilder)
        val counterPartySession = session.receivers.map { initiateFlow(it) }
        val collectSignTxn = subFlow(CollectSignaturesFlow(selfSignedTxn, counterPartySession))

        // notify observers (including myself), if the app is listening
        val txn = subFlow(FinalityFlow(collectSignTxn, counterPartySession))
        return txn
    }
}

@InitiatedBy(CloseSessionInfoFlow::class)
class CloseSessionInfoFlowResponder(private val otherSession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {

        val transactionSigner = object : SignTransactionFlow(otherSession) {
            @Suspendable
            override fun checkTransaction(stx: SignedTransaction) {
            }
        }
        val signTxn = subFlow(transactionSigner)
        return subFlow(ReceiveFinalityFlow(otherSession, signTxn.id))
    }
}
