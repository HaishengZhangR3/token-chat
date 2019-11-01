package com.r3.corda.lib.chat.contracts

import com.r3.corda.lib.chat.contracts.commands.CloseSession
import com.r3.corda.lib.chat.contracts.states.ChatSessionInfo
import com.r3.corda.lib.chat.contracts.states.ChatStatus
import com.r3.corda.lib.tokens.contracts.EvolvableTokenContract
import com.r3.corda.lib.tokens.contracts.commands.Create
import com.r3.corda.lib.tokens.contracts.commands.EvolvableTokenTypeCommand
import com.r3.corda.lib.tokens.contracts.commands.Update
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.transactions.LedgerTransaction

class ChatSessionInfoContract : EvolvableTokenContract(), Contract {
    companion object {
        @JvmStatic
        val CHAT_SESSION_INFO_CONTRACT_ID = "com.r3.corda.lib.chat.contracts.ChatSessionInfoContract"
    }

    override fun additionalCreateChecks(tx: LedgerTransaction) {

        // the event in tx: "Create" a evolvable token event
        // the in/out/ref in tx: 0 input, 1 output, 0 ref
        val command = tx.commands.requireSingleCommand<Create>()

        require(tx.inputStates.isEmpty()) { "There should be no input." }
        require(tx.outputStates.size == 1) { "There should only be one output." }
        require(tx.outputStates.single() is ChatSessionInfo) { "The output should be ChatSessionInfo instance." }
        require(command.signers.size == 1) { "There should be only one required signer." }

        val output = tx.outputStates.single() as ChatSessionInfo
        checkChatInfo(output)
    }

    override fun additionalUpdateChecks(tx: LedgerTransaction) {

        // the event in tx: "Update" a evolvable token event
        // the in/out/ref in tx: 1 input, 1 output, 0 ref
        val command = tx.commands.requireSingleCommand<Update>()

        require(tx.inputStates.size == 1) { "There should only be one input." }
        require(tx.inputStates.single() is ChatSessionInfo) { "The input should be ChatSessionInfo instance." }
        require(tx.outputStates.size == 1) { "There should only be one output." }
        require(tx.outputStates.single() is ChatSessionInfo) { "The output should be ChatSessionInfo instance." }
        require(command.signers.size == 1) { "There should be only one required signer." }

        val input = tx.inputStates.single() as ChatSessionInfo
        checkChatInfo(input)

        val output = tx.outputStates.single() as ChatSessionInfo
        checkChatInfo(output)
    }

    override fun verify(tx: LedgerTransaction) {
        // EvolvableTokenContract::verify will handle official Create and Update commands, the customer command will be here
        super.verify(tx)

        val command = tx.commands.requireSingleCommand<EvolvableTokenTypeCommand>()
        when (command.value) {
            is CloseSession -> {
                // the in/out/ref in tx: 1 input, 0 output, 0 ref
                require(tx.inputStates.size == 1) { "There should only one input." }
                require(tx.inputStates.single() is ChatSessionInfo) { "The input should be ChatSessionInfo instance." }
                require(tx.outputStates.isEmpty()) { "There should be no output." }
                require(command.signers.size > 1) { "There should be more than one required signer." }

                val input = tx.inputStates.single() as ChatSessionInfo
                checkChatInfo(input)
            }
            // there are others commands: Create and Update, so don't throw exception here
        }
    }

    private fun checkChatInfo(chatSessionInfo: ChatSessionInfo) {
        require(chatSessionInfo.receivers.isNotEmpty()) { "The receivers must not be empty." }
        require(!chatSessionInfo.receivers.contains(chatSessionInfo.admin)) { "Admin must not be in receivers." }
        require(chatSessionInfo.receivers.distinct().size == chatSessionInfo.receivers.size) { "Receiver list should not have duplicate." }
        require(chatSessionInfo.status == ChatStatus.ACTIVE) { "The chat status must be Active." }
    }
}
