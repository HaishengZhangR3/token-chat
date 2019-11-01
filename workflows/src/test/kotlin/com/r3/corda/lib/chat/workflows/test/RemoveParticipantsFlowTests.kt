package com.r3.corda.lib.chat.workflows.test

import com.r3.corda.lib.chat.contracts.states.ChatMetaInfo
import com.r3.corda.lib.chat.workflows.flows.CreateChatFlow
import com.r3.corda.lib.chat.workflows.flows.RemoveParticipantsFlow
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

class RemoveParticipantsFlowTests {

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
    fun `should be possible to remove participants from a chat`() {

        // 1 create one
        val newChatFlow = nodeA.startFlow(CreateChatFlow(
                subject = "subject",
                content = "content",
                receivers = listOf(nodeB.info.legalIdentities.single(), nodeC.info.legalIdentities.single())
        ))
        network.runNetwork()
        newChatFlow.getOrThrow()

        val chatInB = nodeB.services.vaultService.queryBy(ChatMetaInfo::class.java).states.single().state.data

        // 2. remove participants
        val removeParticipantsFlow = nodeA.startFlow(
                RemoveParticipantsFlow(
                        toRemove = listOf(nodeC.info.legalIdentities.single()),
                        chatId = chatInB.linearId
                )
        )

        network.runNetwork()
        removeParticipantsFlow.getOrThrow()

        // A and B should have meta info while C should not have
        val metaStateRefA = nodeA.services.vaultService.queryBy(ChatMetaInfo::class.java).states
        val metaStateRefB = nodeB.services.vaultService.queryBy(ChatMetaInfo::class.java).states
        val metaStateRefC = nodeC.services.vaultService.queryBy(ChatMetaInfo::class.java).states
        Assert.assertEquals(metaStateRefA.size, 1)
        Assert.assertEquals(metaStateRefB.size, 1)
        Assert.assertEquals(metaStateRefC.size, 0)

        val metaA = metaStateRefA.single().state.data
        val metaB = metaStateRefB.single().state.data

        val oldParticipants = chatInB.participants
        val expectedParticipants = oldParticipants - nodeC.info.legalIdentities.single()
        val newParticipants = metaA.participants

        Assert.assertTrue(expectedParticipants.size == newParticipants.size)
        Assert.assertTrue((expectedParticipants - newParticipants).isEmpty())
        Assert.assertTrue((newParticipants - expectedParticipants).isEmpty())

        Assert.assertEquals(
                listOf(metaA.linearId, metaB.linearId).distinct().size,
                1
        )
    }

    @Test
    fun `add participants should follow constrain`() {

        // 1 create one
        val newChatFlow = nodeA.startFlow(CreateChatFlow(
                subject = "subject",
                content = "content",
                receivers = listOf(nodeB.info.legalIdentities.single(), nodeC.info.legalIdentities.single())
        ))
        network.runNetwork()
        newChatFlow.getOrThrow()

        val oldChatMetaA = nodeA.services.vaultService.queryBy(ChatMetaInfo::class.java).states.single().state.data
        val oldChatMetaB = nodeB.services.vaultService.queryBy(ChatMetaInfo::class.java).states.single().state.data
        val oldChatMetaC = nodeC.services.vaultService.queryBy(ChatMetaInfo::class.java).states.single().state.data

        // 2. remove participants
        val removeParticipantsFlow = nodeA.startFlow(
                RemoveParticipantsFlow(
                        toRemove = listOf(nodeC.info.legalIdentities.single()),
                        chatId = oldChatMetaA.linearId
                )
        )

        network.runNetwork()
        removeParticipantsFlow.getOrThrow()

        val chatMetaA = nodeA.services.vaultService.queryBy(ChatMetaInfo::class.java).states.single().state.data
        val chatMetaB = nodeB.services.vaultService.queryBy(ChatMetaInfo::class.java).states.single().state.data

        // the following tests are based on "state machine" constrains
        // linearId and subject must not change
        Assert.assertEquals(
                listOf(chatMetaA.linearId, chatMetaB.linearId,
                        oldChatMetaA.linearId, oldChatMetaB.linearId, oldChatMetaC.linearId).distinct().size,
                1)
        Assert.assertEquals(
                listOf(chatMetaA.subject, chatMetaB.subject,
                        oldChatMetaA.subject, oldChatMetaB.subject, oldChatMetaC.subject).distinct().size,
                1)

        // admin must not change
        val admins = listOf(
                chatMetaA.admin,
                chatMetaB.admin,
                oldChatMetaA.admin,
                oldChatMetaB.admin,
                oldChatMetaC.admin
        ).distinct()
        Assert.assertEquals(admins.size, 1)

        // admin must be the initiator
        Assert.assertEquals(nodeA.info.legalIdentities.single(), admins.single())

        // receivers list must change to new participants
        val oldReceivers = oldChatMetaA.receivers
        val expectedReceivers = oldReceivers - nodeC.info.legalIdentities.single()
        val newReceiversA = chatMetaA.receivers
        val newReceiversB = chatMetaB.receivers

        Assert.assertEquals(newReceiversA, newReceiversB)
        Assert.assertEquals((newReceiversA - expectedReceivers).size, 0)
        Assert.assertEquals((expectedReceivers - newReceiversA).size, 0)
    }
}
