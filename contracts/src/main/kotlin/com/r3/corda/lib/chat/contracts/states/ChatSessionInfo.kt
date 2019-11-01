package com.r3.corda.lib.chat.contracts.states

import com.r3.corda.lib.chat.contracts.ChatSessionInfoContract
import com.r3.corda.lib.chat.contracts.internal.schemas.ChatSessionInfoSchema
import com.r3.corda.lib.chat.contracts.internal.schemas.PersistentChatSessionInfo
import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.CordaSerializable
import java.time.Instant

@CordaSerializable
sealed class ChatStatus {
    object ACTIVE : ChatStatus()
    object CLOSED : ChatStatus()
}

@CordaSerializable
@BelongsToContract(ChatSessionInfoContract::class)
data class ChatSessionInfo(
        override val linearId: UniqueIdentifier,
        val created: Instant = Instant.now(),
        val admin: Party,
        val receivers: List<Party>,
        val subject: String,
        val status: ChatStatus = ChatStatus.ACTIVE
) : EvolvableTokenType(), QueryableState, LinearState {

    override fun generateMappedObject(schema: MappedSchema): PersistentState =
            when (schema) {
                is ChatSessionInfoSchema ->
                    PersistentChatSessionInfo(
                            identifier = linearId.id,
                            created = created,
                            admin = admin,
                            chatReceiverList = receivers,
                            status = status.toString(),
                            subject = subject,
                            participants = participants
                    )
                else ->
                    throw IllegalStateException("Cannot construct instance of ${this.javaClass} from Schema: $schema")
            }

    override val maintainers: List<Party> get() = listOf(admin)
    override val participants: List<AbstractParty> get() = receivers + admin
    override val fractionDigits = 0

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(ChatSessionInfoSchema)
    override fun toString(): String {
        return "ChatSessionInfo(linearId=$linearId, created=$created, admin=$admin, receivers=$receivers, subject='$subject', status=$status, fractionDigits=$fractionDigits)"
    }
}
