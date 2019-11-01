package com.r3.corda.lib.chat.workflows.test

import com.r3.corda.lib.chat.contracts.states.ChatMessage
import com.r3.corda.lib.chat.contracts.states.ChatSessionInfo
import com.r3.corda.lib.chat.workflows.flows.*
import com.r3.corda.lib.chat.workflows.test.observer.ObserverUtils
import net.corda.core.contracts.UniqueIdentifier
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

class CloseSessionFlowTests {

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

        val f1 = nodeA.startFlow(CreateSessionFlow(
                subject = "subject",
                content = "content new",
                receivers = listOf(nodeB.info.legalIdentities.single())
        ))
        network.runNetwork()
        val msg = f1.getOrThrow()
        val chatId = UniqueIdentifier.fromString(msg.state.data.token.tokenIdentifier)

        // 3. close chat
        val f2 = nodeA.startFlow(
                CloseSessionFlow(
                        chatId = chatId
                )
        )
        network.runNetwork()
        f2.getOrThrow()

        // after all and all, there should be 0 session and 0 message on ledge in each node
        val sessionA = nodeA.services.vaultService.queryBy(ChatSessionInfo::class.java).states
        val sessionB = nodeB.services.vaultService.queryBy(ChatSessionInfo::class.java).states
        val sessionC = nodeC.services.vaultService.queryBy(ChatSessionInfo::class.java).states

        val chatMessagesA = nodeA.services.vaultService.queryBy(ChatMessage::class.java).states
        val chatMessagesB = nodeB.services.vaultService.queryBy(ChatMessage::class.java).states
        val chatMessagesC = nodeC.services.vaultService.queryBy(ChatMessage::class.java).states

        Assert.assertTrue(sessionA.isEmpty())
        Assert.assertTrue(sessionB.isEmpty())
        Assert.assertTrue(sessionC.isEmpty())

        Assert.assertTrue(chatMessagesA.isEmpty())
        Assert.assertTrue(chatMessagesB.isEmpty())
        Assert.assertTrue(chatMessagesC.isEmpty())
    }
}
