package com.r3.corda.lib.chat.workflows.test.internal

import com.r3.corda.lib.chat.contracts.states.ChatSessionInfo
import com.r3.corda.lib.chat.workflows.flows.internal.CreateSessionInfoFlow
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

class CreateSessionInfoFlowTests {

    lateinit var network: MockNetwork
    lateinit var nodeA: StartedMockNode
    lateinit var nodeB: StartedMockNode

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
        ObserverUtils.registerObserver(listOf(nodeA, nodeB))

        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    @Test
    fun `should be possible to create a chat`() {

        val chatFlow = nodeA.startFlow(CreateSessionInfoFlow(
                subject = "subject",
                receivers = listOf(nodeB.info.legalIdentities.single())
        ))
        network.runNetwork()
        chatFlow.getOrThrow()

        //check whether the created one in node B is same as that in the DB of host node A
        val sessionA = nodeA.services.vaultService.queryBy(ChatSessionInfo::class.java).states.single().state.data
        val sessionB = nodeB.services.vaultService.queryBy(ChatSessionInfo::class.java).states.single().state.data
        Assert.assertTrue(sessionB.linearId == sessionA.linearId)

        // same chat session in two nodes should have same participants
        val sessionPartiesA = sessionA.participants
        val sessionPartiesB = sessionB.participants
        Assert.assertEquals(sessionPartiesA.size, 2)
        Assert.assertEquals(sessionPartiesB.size, 2)

        Assert.assertTrue(sessionPartiesA.subtract(sessionPartiesB).isEmpty())
        Assert.assertTrue(sessionPartiesB.subtract(sessionPartiesA).isEmpty())

    }
}
