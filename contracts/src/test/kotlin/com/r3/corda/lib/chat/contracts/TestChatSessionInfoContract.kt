package com.r3.corda.lib.chat.contracts

import com.r3.corda.lib.chat.contracts.commands.CloseSession
import com.r3.corda.lib.chat.contracts.states.ChatSessionInfo
import com.r3.corda.lib.chat.contracts.states.ChatStatus
import com.r3.corda.lib.tokens.contracts.commands.Create
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.contracts.UniqueIdentifier
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test
import java.time.Instant
import java.util.*

class TestChatSessionInfoContract {

    class DummyCommand : TypeOnlyCommandData()
    private var ledgerServices = MockServices(listOf("com.r3.corda.lib.chat"))
    private val partyA = ledgerServices

    @Test
    fun mustCreateSession() {
        val id = UniqueIdentifier.fromString(UUID.randomUUID().toString())
        val session = ChatSessionInfo(
                linearId = id,
                created = Instant.now(),
                admin = ALICE.party,
                receivers = listOf( BOB.party),
                subject = "subject",
                status = ChatStatus.ACTIVE
        )

        ledgerServices.ledger {
            transaction {
                output(ChatSessionInfoContract.CHAT_SESSION_INFO_CONTRACT_ID, session)
                command(listOf(ALICE.publicKey), Create())
                this.verifies()
            }
        }
    }

    @Test
    fun mustCloseSession() {
        val id = UniqueIdentifier.fromString(UUID.randomUUID().toString())
        val session = ChatSessionInfo(
                linearId = id,
                created = Instant.now(),
                admin = ALICE.party,
                receivers = listOf( BOB.party),
                subject = "subject",
                status = ChatStatus.ACTIVE
        )

        ledgerServices.ledger {
            transaction {
                input(ChatSessionInfoContract.CHAT_SESSION_INFO_CONTRACT_ID, session)
                command(listOf(ALICE.publicKey), CloseSession())
                this.verifies()
            }
        }
    }

}
