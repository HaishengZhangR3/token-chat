package com.r3.corda.lib.chat.contracts

import com.r3.corda.lib.chat.contracts.commands.CreateMessage
import com.r3.corda.lib.chat.contracts.states.ChatMessage
import com.r3.corda.lib.chat.contracts.states.ChatSessionInfo
import com.r3.corda.lib.chat.contracts.states.ChatStatus
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.contracts.UniqueIdentifier
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test
import java.time.Instant
import java.util.*

class TestChatMessageContract {

    class DummyCommand : TypeOnlyCommandData()
    private var ledgerServices = MockServices(listOf("com.r3.corda.lib.chat"))
    private val partyA = ledgerServices

    @Test
    fun mustIncludeIssueCommand() {
        val id = UniqueIdentifier.fromString(UUID.randomUUID().toString())
        val session = ChatSessionInfo(
                linearId = id,
                created = Instant.now(),
                admin = ALICE.party,
                receivers = listOf( BOB.party),
                subject = "subject",
                status = ChatStatus.ACTIVE
        )
        val chatPointer = session.toPointer<ChatSessionInfo>()
        val issuedTokenType = chatPointer issuedBy ALICE.party

        val message = ChatMessage(
                token = issuedTokenType,
                linearId = id,
                created = Instant.now(),
                content = "content",
                sender = ALICE.party,
                holder = BOB.party
        )

        // MockServices only assume the version is 1, while reference is only supported in 4, so fails here
        ledgerServices.ledger {
            transaction {
                this.reference(ChatSessionInfoContract.CHAT_SESSION_INFO_CONTRACT_ID, session)
                output(ChatMessageContract.CHAT_MESSAGE_CONTRACT_ID, message)
                command(listOf(ALICE.publicKey), CreateMessage())

                this.verifies()
            }
        }
    }
}
