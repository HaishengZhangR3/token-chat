package com.r3.corda.lib.chat.workflows.test.internal

import com.r3.corda.lib.chat.contracts.states.ChatSessionInfo
import com.r3.corda.lib.chat.workflows.flows.internal.CloseSessionInfoFlow
import com.r3.corda.lib.chat.workflows.flows.internal.CreateSessionInfoFlow
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
        val newChatFlow = nodeA.startFlow(CreateSessionInfoFlow(
                subject = "subject",
                receivers = listOf(nodeB.info.legalIdentities.single())
        ))
        network.runNetwork()
        val chatInfo = newChatFlow.getOrThrow().state.data

        val oldChatSessionA = nodeA.services.vaultService.queryBy(ChatSessionInfo::class.java).states.single().state.data
        val oldChatSessionB = nodeB.services.vaultService.queryBy(ChatSessionInfo::class.java).states.single().state.data
        Assert.assertEquals(oldChatSessionB.receivers, oldChatSessionB.receivers)
        Assert.assertTrue(oldChatSessionA.receivers.contains(nodeB.info.legalIdentities.single()))

        // add receivers to the chat
        val addParticipantsFlow = nodeA.startFlow(
                UpdateReceiversFlow(
                        toAdd = listOf(nodeC.info.legalIdentities.single()),
                        chatId = chatInfo.linearId
                )
        )

        network.runNetwork()
        addParticipantsFlow.getOrThrow()

        val addedSessionA = nodeA.services.vaultService.queryBy(ChatSessionInfo::class.java).states.single().state.data
        val addedSessionB = nodeB.services.vaultService.queryBy(ChatSessionInfo::class.java).states.single().state.data
        val addedSessionC = nodeC.services.vaultService.queryBy(ChatSessionInfo::class.java).states.single().state.data
        Assert.assertEquals(addedSessionA.receivers, addedSessionA.receivers)
        Assert.assertEquals(addedSessionA.receivers, addedSessionA.receivers)
        Assert.assertTrue(addedSessionA.receivers.contains(nodeB.info.legalIdentities.single()))
        Assert.assertTrue(addedSessionA.receivers.contains(nodeC.info.legalIdentities.single()))

        // receivers list must change to new participants
        val addResult = oldChatSessionA.receivers + nodeC.info.legalIdentities.single()
        Assert.assertEquals((addedSessionC.receivers - addResult).size, 0)
        Assert.assertEquals((addResult - addedSessionC.receivers).size, 0)
        Assert.assertEquals(addedSessionA.receivers, addedSessionB.receivers)
        Assert.assertEquals(addedSessionA.receivers, addedSessionC.receivers)

        // remove receivers to the chat
        val removeParticipantsFlow = nodeA.startFlow(
                UpdateReceiversFlow(
                        toRemove = listOf(nodeB.info.legalIdentities.single()),
                        chatId = chatInfo.linearId
                )
        )

        network.runNetwork()
        removeParticipantsFlow.getOrThrow()

        val removedSessionA = nodeA.services.vaultService.queryBy(ChatSessionInfo::class.java).states.single().state.data
        val removedSessionB = nodeB.services.vaultService.queryBy(ChatSessionInfo::class.java).states
        val removedSessionC = nodeC.services.vaultService.queryBy(ChatSessionInfo::class.java).states.single().state.data
        Assert.assertTrue(removedSessionB.isEmpty())
        Assert.assertEquals(removedSessionA.receivers, removedSessionC.receivers)
        Assert.assertTrue(removedSessionA.receivers.contains(nodeC.info.legalIdentities.single()))

        val oldParticipants = addedSessionB.participants
        val removeResult = oldParticipants - nodeB.info.legalIdentities.single()
        val newParticipants = removedSessionA.participants

        Assert.assertTrue(removeResult.size == newParticipants.size)
        Assert.assertTrue((removeResult - newParticipants).isEmpty())
        Assert.assertTrue((newParticipants - removeResult).isEmpty())

        // 4. close chat
        val closeFlow = nodeA.startFlow(
                CloseSessionInfoFlow(
                        chatId = chatInfo.linearId
                )
        )
        network.runNetwork()
        closeFlow.getOrThrow()

        // there are 0 chat on ledge in each node
        val closedSessionA = nodeA.services.vaultService.queryBy(ChatSessionInfo::class.java).states
        val closedSessionB = nodeB.services.vaultService.queryBy(ChatSessionInfo::class.java).states
        val closedSessionC = nodeC.services.vaultService.queryBy(ChatSessionInfo::class.java).states

        Assert.assertTrue(closedSessionA.isEmpty())
        Assert.assertTrue(closedSessionB.isEmpty())
        Assert.assertTrue(closedSessionC.isEmpty())
    }
}
