package com.r3.corda.lib.chat.contracts

import com.r3.corda.lib.chat.contracts.commands.CloseMeta
import com.r3.corda.lib.chat.contracts.commands.CreateMeta
import com.r3.corda.lib.chat.contracts.states.ChatMetaInfo
import com.r3.corda.lib.chat.contracts.states.ChatStatus
import com.r3.corda.lib.tokens.contracts.commands.Create
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.contracts.UniqueIdentifier
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test
import java.time.Instant
import java.util.*

class TestChatMetaInfoContract {

    class DummyCommand : TypeOnlyCommandData()
    private var ledgerServices = MockServices(listOf("com.r3.corda.lib.chat"))
    private val partyA = ledgerServices

    @Test
    fun mustCreateMeta() {
        val id = UniqueIdentifier.fromString(UUID.randomUUID().toString())
        val meta = ChatMetaInfo(
                linearId = id,
                created = Instant.now(),
                admin = ALICE.party,
                receivers = listOf( BOB.party),
                subject = "subject",
                status = ChatStatus.ACTIVE
        )

        ledgerServices.ledger {
            transaction {
                output(ChatMetaInfoContract.CHAT_METAINFO_CONTRACT_ID, meta)
                command(listOf(ALICE.publicKey), Create())
                this.verifies()
            }
        }
    }

    @Test
    fun mustCloseMeta() {
        val id = UniqueIdentifier.fromString(UUID.randomUUID().toString())
        val meta = ChatMetaInfo(
                linearId = id,
                created = Instant.now(),
                admin = ALICE.party,
                receivers = listOf( BOB.party),
                subject = "subject",
                status = ChatStatus.ACTIVE
        )

        ledgerServices.ledger {
            transaction {
                input(ChatMetaInfoContract.CHAT_METAINFO_CONTRACT_ID, meta)
                command(listOf(ALICE.publicKey), CloseMeta())
                this.verifies()
            }
        }
    }

}
