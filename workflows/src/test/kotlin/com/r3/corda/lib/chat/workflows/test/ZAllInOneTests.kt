package com.r3.corda.lib.chat.workflows.test

import com.r3.corda.lib.chat.contracts.states.ChatMessage
import com.r3.corda.lib.chat.contracts.states.ChatMetaInfo
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

class ZAllInOneTests {

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

        val f1 = nodeA.startFlow(CreateChatFlow(
                subject = "subject",
                content = "content new",
                receivers = listOf(nodeB.info.legalIdentities.single())
        ))
        network.runNetwork()
        val msg = f1.getOrThrow()
        val chatId = UniqueIdentifier.fromString(msg.state.data.token.tokenIdentifier)

        val f2 = nodeA.startFlow(
                SendMessageFlow(
                        content = "reply content to a chat 1",
                        chatId = chatId
                )
        )
        network.runNetwork()
        f2.getOrThrow()

        val f3 = nodeA.startFlow(
                AddParticipantsFlow(
                        toAdd = listOf(nodeC.info.legalIdentities.single()),
                        chatId = chatId
                )
        )

        network.runNetwork()
        f3.getOrThrow()

        val f4 = nodeB.startFlow(
                SendMessageFlow(
                        content = "reply content to a chat 2",
                        chatId = chatId
                )
        )
        network.runNetwork()
        f4.getOrThrow()

        val f5 = nodeA.startFlow(
                RemoveParticipantsFlow(
                        toRemove = listOf(nodeB.info.legalIdentities.single()),
                        chatId = chatId
                )
        )

        network.runNetwork()
        f5.getOrThrow()

        // 2 send message to the chat
        val f6 = nodeC.startFlow(
                SendMessageFlow(
                        content = "reply content to a chat 3",
                        chatId = chatId
                )
        )
        network.runNetwork()
        f6.getOrThrow()


        // 3. close chat
        val f7 = nodeA.startFlow(
                CloseChatFlow(
                        chatId = chatId
                )
        )
        network.runNetwork()
        f7.getOrThrow()


        // after all and all, there should be 0 meta and 0 message on ledge in each node
        val chatMetaA = nodeA.services.vaultService.queryBy(ChatMetaInfo::class.java).states
        val chatMetaB = nodeB.services.vaultService.queryBy(ChatMetaInfo::class.java).states
        val chatMetaC = nodeC.services.vaultService.queryBy(ChatMetaInfo::class.java).states

        val chatMessagesA = nodeA.services.vaultService.queryBy(ChatMessage::class.java).states
        val chatMessagesB = nodeB.services.vaultService.queryBy(ChatMessage::class.java).states
        val chatMessagesC = nodeC.services.vaultService.queryBy(ChatMessage::class.java).states

        Assert.assertTrue(chatMetaA.isEmpty())
        Assert.assertTrue(chatMetaB.isEmpty())
        Assert.assertTrue(chatMetaC.isEmpty())

        Assert.assertTrue(chatMessagesA.isEmpty())
        Assert.assertTrue(chatMessagesB.isEmpty())
        Assert.assertTrue(chatMessagesC.isEmpty())
    }
}
