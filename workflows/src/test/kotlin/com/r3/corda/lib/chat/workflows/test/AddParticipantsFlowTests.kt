package com.r3.corda.lib.chat.workflows.test

import com.r3.corda.lib.chat.contracts.states.ChatMetaInfo
import com.r3.corda.lib.chat.workflows.flows.AddParticipantsFlow
import com.r3.corda.lib.chat.workflows.flows.CreateChatFlow
import com.r3.corda.lib.chat.workflows.test.observer.ObserverUtils
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class AddParticipantsFlowTests {

    lateinit var network: MockNetwork
    lateinit var nodeA: StartedMockNode
    lateinit var nodeB: StartedMockNode
    lateinit var nodeC: StartedMockNode

    @Before
    fun setup() {
        network = MockNetwork(
                MockNetworkParameters(
                        networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
                        cordappsForAllNodes = listOf(
                                TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                                TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
                                TestCordapp.findCordapp("com.r3.corda.lib.chat.contracts"),
                                TestCordapp.findCordapp("com.r3.corda.lib.chat.workflows")
                        )
                )
        )
        nodeA = network.createPartyNode()
        nodeB = network.createPartyNode()
        nodeC = network.createPartyNode()
        ObserverUtils.registerObserver(listOf(nodeA, nodeB, nodeC))

        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    @Test
    fun `should be possible to add participants to a chat`() {

        // 1 create one
        val newChatFlow = nodeA.startFlow(CreateChatFlow(
                subject = "subject",
                content = "content",
                receivers = listOf(nodeB.info.legalIdentities.single())
        ))
        network.runNetwork()
        newChatFlow.getOrThrow()

        val oldChatMetaA = nodeA.services.vaultService.queryBy(ChatMetaInfo::class.java).states.single().state.data
        val oldChatMetaB = nodeB.services.vaultService.queryBy(ChatMetaInfo::class.java).states.single().state.data

        // 2. add new participants
        val addParticipantsFlow = nodeA.startFlow(
                AddParticipantsFlow(
                        toAdd = listOf(nodeC.info.legalIdentities.single()),
                        chatId = oldChatMetaA.linearId
                )
        )

        network.runNetwork()
        addParticipantsFlow.getOrThrow()

        // old MetaInfo is consumed in A,B, and new one should be in A,B and C
        val chatMetaA = nodeA.services.vaultService.queryBy(ChatMetaInfo::class.java).states.single().state.data
        val chatMetaB = nodeB.services.vaultService.queryBy(ChatMetaInfo::class.java).states.single().state.data
        val chatMetaC = nodeC.services.vaultService.queryBy(ChatMetaInfo::class.java).states.single().state.data

        Assert.assertTrue((chatMetaA.receivers - chatMetaB.receivers).isEmpty())
        Assert.assertTrue((chatMetaB.receivers - chatMetaC.receivers).isEmpty())

        val expectedParticipants = oldChatMetaA.receivers + nodeC.info.legalIdentities.single()
        Assert.assertEquals((chatMetaC.receivers - expectedParticipants).size, 0)
        Assert.assertEquals((expectedParticipants - chatMetaC.receivers).size, 0)

        Assert.assertEquals(
                listOf(chatMetaA.linearId, chatMetaB.linearId, chatMetaC.linearId,
                        oldChatMetaA.linearId, oldChatMetaB.linearId).distinct().size,
                1)


    }

    @Test
    fun `add participants should follow constrain`() {

        // 1 create one
        val newChatFlow = nodeA.startFlow(CreateChatFlow(
                subject = "subject",
                content = "content",
                receivers = listOf(nodeB.info.legalIdentities.single())
        ))
        network.runNetwork()
        newChatFlow.getOrThrow()

        val oldChatMetaA = nodeA.services.vaultService.queryBy(ChatMetaInfo::class.java).states.single().state.data
        val oldChatMetaB = nodeB.services.vaultService.queryBy(ChatMetaInfo::class.java).states.single().state.data

        // 2. add new participants
        val addParticipantsFlow = nodeA.startFlow(
                AddParticipantsFlow(
                        toAdd = listOf(nodeC.info.legalIdentities.single()),
                        chatId = oldChatMetaA.linearId
                )
        )

        network.runNetwork()
        addParticipantsFlow.getOrThrow()

        // old MetaInfo is consumed in A,B, and new one should be in A,B and C
        val chatMetaA = nodeA.services.vaultService.queryBy(ChatMetaInfo::class.java).states.single().state.data
        val chatMetaB = nodeB.services.vaultService.queryBy(ChatMetaInfo::class.java).states.single().state.data
        val chatMetaC = nodeC.services.vaultService.queryBy(ChatMetaInfo::class.java).states.single().state.data

        val expectedParticipants = oldChatMetaA.receivers + nodeC.info.legalIdentities.single()

        // the following tests are based on "state machine" constrains
        // linearId and subject must not change
        Assert.assertEquals(
                listOf(chatMetaA.linearId, chatMetaB.linearId, chatMetaC.linearId,
                        oldChatMetaA.linearId, oldChatMetaB.linearId).distinct().size,
                1)
        Assert.assertEquals(
                listOf(chatMetaA.subject, chatMetaB.subject, chatMetaC.subject,
                        oldChatMetaA.subject, oldChatMetaB.subject).distinct().size,
                1)

        // created time must change
        val newChatDate = chatMetaA.created
        val oldChatDate = oldChatMetaA.created
        Assert.assertTrue(oldChatDate < newChatDate)

        val admins = listOf(
                chatMetaA.admin,
                chatMetaB.admin,
                chatMetaC.admin,
                oldChatMetaA.admin,
                oldChatMetaB.admin
        ).distinct()

        // admin must not change
        Assert.assertEquals(admins.size, 1)

        // admin must be the initiator
        Assert.assertEquals(nodeA.info.legalIdentities.single(), admins.single())

        // receivers list must change to new participants
        Assert.assertEquals(chatMetaA.receivers, chatMetaB.receivers)
        Assert.assertEquals(chatMetaA.receivers, chatMetaC.receivers)
        Assert.assertEquals((chatMetaC.receivers - expectedParticipants).size, 0)
        Assert.assertEquals((expectedParticipants - chatMetaC.receivers).size, 0)
    }

}
