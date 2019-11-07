package com.r3.corda.lib.chat.contracts

import com.r3.corda.lib.chat.contracts.commands.CloseMessages
import com.r3.corda.lib.chat.contracts.states.ChatMessage
import com.r3.corda.lib.chat.contracts.states.ChatSessionInfo
import com.r3.corda.lib.tokens.contracts.commands.IssueTokenCommand
import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

class ChatMessageContract : Contract {
    companion object {
        @JvmStatic
        val CHAT_MESSAGE_CONTRACT_ID = "com.r3.corda.lib.chat.contracts.ChatMessageContract"
    }

    override fun verify(tx: LedgerTransaction) {

        val commands = tx.commands
        require(commands.size == 1) { "There should be only one command in transaction." }

        // there are two possible commands:
        // IssueTokenCommand : TokenCommand: will pass check to NonFungibleTokenContract
        // CloseMessages : ChatCommand: will handle here
        val command = commands.single()
        when (command.value) {
            is IssueTokenCommand -> {

                //IssueTokenCommand: 0 inout, 1 output, 1 ref
                require(tx.referenceStates.size == 1) { "There should be a reference input." }
                require(tx.referenceStates.single() is ChatSessionInfo) { "The reference input should be ChatSessionInfo instance." }
                require(tx.inputStates.isEmpty()) { "There should be no input." }
                require(tx.outputStates.size == 1) { "There should only be one output." }
                require(tx.outputStates.single() is ChatMessage) { "The output should be ChatMessage instance." }

                val session = tx.referenceStates.single() as ChatSessionInfo
                val chatMessage = tx.outputStates.single() as ChatMessage
                require(chatMessage.token.tokenIdentifier == session.linearId.toString()) {
                    "chatId/linearId in two states must be same."
                }

                val allParticipants = session.receivers + session.admin
                require(allParticipants.contains(chatMessage.sender)) {"Message sender must be in the existing session receivers list."}
                require(allParticipants.contains(chatMessage.holder)) {"Message holder must be in the existing session receivers list."}

                val issuerKey = chatMessage.issuer.owningKey
                val issueSigners = command.signers

                require(issueSigners.size == 1) { "There should only be one required signer." }
                require(issuerKey in issueSigners) {
                    "The issuer must be the signing party when a token is issued."
                }
            }
            is CloseMessages -> {
                require(tx.referenceStates.size == 1) { "There should be a reference input." }
                require(tx.referenceStates.single() is ChatSessionInfo) { "The reference input should be ChatSessionInfo instance." }
                require(tx.inputStates.isNotEmpty()) { "There should be at least one input." }
                require(tx.outputStates.isEmpty()) { "There should be no output." }

                val session = tx.referenceStates.single() as ChatSessionInfo
                val chatIds = mutableSetOf<String>()
                tx.inputStates.forEach {
                    require(it is ChatMessage) { "The output should be ChatMessage instance." }
                    it as ChatMessage
                    chatIds.add(it.token.tokenIdentifier)
                }
                chatIds.add(session.linearId.toString())
                require(chatIds.size == 1) { "All of the inputs should have same chat ID." }

                val requiredSigners = command.signers
                require(requiredSigners.size == 1) { "There should be only one required signer." }
            }
        }
    }
}
