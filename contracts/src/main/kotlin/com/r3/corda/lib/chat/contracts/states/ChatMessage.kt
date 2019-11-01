package com.r3.corda.lib.chat.contracts.states

import com.r3.corda.lib.chat.contracts.ChatMessageContract
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.time.Instant

@CordaSerializable
@BelongsToContract(ChatMessageContract::class)
class ChatMessage(
        token: IssuedTokenType,                     // chat ID is hold here
        override val linearId: UniqueIdentifier,    // this is message id
        val created: Instant = Instant.now(),
        val content: String,
        val sender: Party,                  // message sender
        override val holder: AbstractParty  // message receiver
) : NonFungibleToken(
        token = token,
        holder = holder,
        linearId = linearId
) {
    override fun toString(): String {
        return "ChatMessage(linearId=$linearId, created=$created, content='$content', sender=$sender, holder=$holder)"
    }
}
