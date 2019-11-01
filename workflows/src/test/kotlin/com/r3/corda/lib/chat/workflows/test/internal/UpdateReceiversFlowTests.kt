package com.r3.corda.lib.chat.workflows.test.internal

import com.r3.corda.lib.chat.contracts.states.ChatMetaInfo
import com.r3.corda.lib.chat.workflows.flows.internal.CloseMetaInfoFlow
import com.r3.corda.lib.chat.workflows.flows.internal.CreateMetaInfoFlow
import com.r3.corda.lib.chat.workflows.flows.internal.UpdateReceiversFlow
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

class UpdateReceiversFlowTests {

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
    fun `should be possible to close a chat`() {

        // 1 create one
        val newChatFlow = nodeA.startFlow(CreateMetaInfoFlow(
                subject = "subject",
                receivers = listOf(nodeB.info.legalIdentities.single())
        ))
        network.runNetwork()
        newChatFlow.getOrThrow()

        val newChatMetaInfoA = nodeA.services.vaultService.queryBy(ChatMetaInfo::class.java).states.single().state.data
        val newChatMetaInfoB = nodeB.services.vaultService.queryBy(ChatMetaInfo::class.java).states.single().state.data

        // 2 send message to the chat
        val addParticipantsFlow = nodeA.startFlow(
                UpdateReceiversFlow(
                        toAdd = listOf(nodeC.info.legalIdentities.single()),
                        chatId = newChatMetaInfoA.linearId
                )
        )

        network.runNetwork()
        addParticipantsFlow.getOrThrow()

        val addedMetaA = nodeA.services.vaultService.queryBy(ChatMetaInfo::class.java).states.single().state.data
        val addedMetaB = nodeB.services.vaultService.queryBy(ChatMetaInfo::class.java).states.single().state.data
        val addedMetaC = nodeC.services.vaultService.queryBy(ChatMetaInfo::class.java).states.single().state.data

        // 3 send message to the chat
        val removeParticipantsFlow = nodeA.startFlow(
                UpdateReceiversFlow(
                        toRemove = listOf(nodeB.info.legalIdentities.single()),
                        chatId = newChatMetaInfoA.linearId
                )
        )

        network.runNetwork()
        removeParticipantsFlow.getOrThrow()

        val removedMetaA = nodeA.services.vaultService.queryBy(ChatMetaInfo::class.java).states.single().state.data
        val removedMetaB = nodeB.services.vaultService.queryBy(ChatMetaInfo::class.java).states
        val removedMetaC = nodeC.services.vaultService.queryBy(ChatMetaInfo::class.java).states.single().state.data
        Assert.assertTrue(removedMetaB.isEmpty())

        // 4. close chat
        val closeFlow = nodeA.startFlow(
                CloseMetaInfoFlow(
                        chatId = newChatMetaInfoA.linearId
                )
        )
        network.runNetwork()
        closeFlow.getOrThrow()

        // there are 0 chat on ledge in each node
        val closedMetaA = nodeA.services.vaultService.queryBy(ChatMetaInfo::class.java).states
        val closedMetaB = nodeB.services.vaultService.queryBy(ChatMetaInfo::class.java).states
        val closedMetaC = nodeC.services.vaultService.queryBy(ChatMetaInfo::class.java).states

        Assert.assertTrue(closedMetaA.isEmpty())
        Assert.assertTrue(closedMetaB.isEmpty())
        Assert.assertTrue(closedMetaC.isEmpty())
    }
}
