package com.r3.corda.lib.chat.contracts

import com.r3.corda.lib.chat.contracts.commands.CloseMessages
import com.r3.corda.lib.chat.contracts.states.ChatMessage
import com.r3.corda.lib.chat.contracts.states.ChatMetaInfo
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
                require(tx.referenceStates.single() is ChatMetaInfo) { "The reference input should be ChatMetaInfo instance." }
                require(tx.inputStates.isEmpty()) { "There should be no input." }
                require(tx.outputStates.size == 1) { "There should only be one output." }
                require(tx.outputStates.single() is ChatMessage) { "The output should be ChatMessage instance." }

                val chatMeta = tx.referenceStates.single() as ChatMetaInfo
                val chatMessage = tx.outputStates.single() as ChatMessage
                require(chatMessage.token.tokenIdentifier == chatMeta.linearId.toString()) {
                    "chatId/linearId in two states must be same."
                }

                val issuerKey = chatMessage.issuer.owningKey
                val issueSigners = command.signers

                require(issueSigners.size == 1) { "There should only be one required signer." }
                require(issuerKey in issueSigners) {
                    "The issuer must be the signing party when a token is issued."
                }
            }
            is CloseMessages -> {
                require(tx.referenceStates.size == 1) { "There should be a reference input." }
                require(tx.referenceStates.single() is ChatMetaInfo) { "The reference input should be ChatMetaInfo instance." }
                require(tx.inputStates.isNotEmpty()) { "There should be at least one input." }
                require(tx.outputStates.isEmpty()) { "There should be no output." }

                val chatMeta = tx.referenceStates.single() as ChatMetaInfo
                val chatIds = mutableSetOf<String>()
                tx.inputStates.forEach {
                    require(it is ChatMessage) { "The output should be ChatMessage instance." }
                    it as ChatMessage
                    chatIds.add(it.token.tokenIdentifier)
                }
                chatIds.add(chatMeta.linearId.toString())
                require(chatIds.size == 1) { "All of the inputs should have same chat ID." }

                val requiredSigners = command.signers
                require(requiredSigners.size == 1) { "There should be only one required signer." }
            }
        }
    }
}
