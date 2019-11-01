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
        val chatInfo = newChatFlow.getOrThrow().state.data

        val oldChatMetaA = nodeA.services.vaultService.queryBy(ChatMetaInfo::class.java).states.single().state.data
        val oldChatMetaB = nodeB.services.vaultService.queryBy(ChatMetaInfo::class.java).states.single().state.data
        Assert.assertEquals(oldChatMetaB.receivers, oldChatMetaB.receivers)
        Assert.assertTrue(oldChatMetaA.receivers.contains(nodeB.info.legalIdentities.single()))

        // add receivers to the chat
        val addParticipantsFlow = nodeA.startFlow(
                UpdateReceiversFlow(
                        toAdd = listOf(nodeC.info.legalIdentities.single()),
                        chatId = chatInfo.linearId
                )
        )

        network.runNetwork()
        addParticipantsFlow.getOrThrow()

        val addedMetaA = nodeA.services.vaultService.queryBy(ChatMetaInfo::class.java).states.single().state.data
        val addedMetaB = nodeB.services.vaultService.queryBy(ChatMetaInfo::class.java).states.single().state.data
        val addedMetaC = nodeC.services.vaultService.queryBy(ChatMetaInfo::class.java).states.single().state.data
        Assert.assertEquals(addedMetaA.receivers, addedMetaA.receivers)
        Assert.assertEquals(addedMetaA.receivers, addedMetaA.receivers)
        Assert.assertTrue(addedMetaA.receivers.contains(nodeB.info.legalIdentities.single()))
        Assert.assertTrue(addedMetaA.receivers.contains(nodeC.info.legalIdentities.single()))

        // receivers list must change to new participants
        val addResult = oldChatMetaA.receivers + nodeC.info.legalIdentities.single()
        Assert.assertEquals((addedMetaC.receivers - addResult).size, 0)
        Assert.assertEquals((addResult - addedMetaC.receivers).size, 0)
        Assert.assertEquals(addedMetaA.receivers, addedMetaB.receivers)
        Assert.assertEquals(addedMetaA.receivers, addedMetaC.receivers)

        // remove receivers to the chat
        val removeParticipantsFlow = nodeA.startFlow(
                UpdateReceiversFlow(
                        toRemove = listOf(nodeB.info.legalIdentities.single()),
                        chatId = chatInfo.linearId
                )
        )

        network.runNetwork()
        removeParticipantsFlow.getOrThrow()

        val removedMetaA = nodeA.services.vaultService.queryBy(ChatMetaInfo::class.java).states.single().state.data
        val removedMetaB = nodeB.services.vaultService.queryBy(ChatMetaInfo::class.java).states
        val removedMetaC = nodeC.services.vaultService.queryBy(ChatMetaInfo::class.java).states.single().state.data
        Assert.assertTrue(removedMetaB.isEmpty())
        Assert.assertEquals(removedMetaA.receivers, removedMetaC.receivers)
        Assert.assertTrue(removedMetaA.receivers.contains(nodeC.info.legalIdentities.single()))

        val oldParticipants = addedMetaB.participants
        val removeResult = oldParticipants - nodeB.info.legalIdentities.single()
        val newParticipants = removedMetaA.participants

        Assert.assertTrue(removeResult.size == newParticipants.size)
        Assert.assertTrue((removeResult - newParticipants).isEmpty())
        Assert.assertTrue((newParticipants - removeResult).isEmpty())

        // 4. close chat
        val closeFlow = nodeA.startFlow(
                CloseMetaInfoFlow(
                        chatId = chatInfo.linearId
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
