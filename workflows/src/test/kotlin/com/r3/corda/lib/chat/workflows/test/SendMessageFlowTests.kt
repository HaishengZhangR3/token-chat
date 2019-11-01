package com.r3.corda.lib.chat.workflows.test

import com.r3.corda.lib.chat.contracts.states.ChatMessage
import com.r3.corda.lib.chat.contracts.states.ChatSessionInfo
import com.r3.corda.lib.chat.workflows.flows.CreateSessionFlow
import com.r3.corda.lib.chat.workflows.flows.SendMessageFlow
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

class SendMessageFlowTests {

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
    fun `should be possible to send message to a chat`() {

        // 1 create one
        val newChatFlow = nodeA.startFlow(CreateSessionFlow(
                subject = "subject",
                content = "content",
                receivers = listOf(nodeB.info.legalIdentities.single())
        ))
        network.runNetwork()
        val newChatMsg = newChatFlow.getOrThrow().state.data

        val newSessionA = nodeA.services.vaultService.queryBy(ChatSessionInfo::class.java).states.single().state.data
        val newSessionB = nodeB.services.vaultService.queryBy(ChatSessionInfo::class.java).states.single().state.data

        // 2 send message to the chat
        val sendMessageFlow = nodeB.startFlow(
                SendMessageFlow(
                        content = "reply content to a chat",
                        chatId = newSessionA.linearId
                )
        )

        network.runNetwork()
        val sendMessage = sendMessageFlow.getOrThrow().state.data

        // there are one chat session on ledge in each node
        val sendMessageSessionStateRefA = nodeA.services.vaultService.queryBy(ChatSessionInfo::class.java).states
        val sendMessageSessionStateRefB = nodeB.services.vaultService.queryBy(ChatSessionInfo::class.java).states
        Assert.assertTrue(sendMessageSessionStateRefA.size == 1)
        Assert.assertTrue(sendMessageSessionStateRefB.size == 1)

        val sendMessageChatSessionA = sendMessageSessionStateRefA.single().state.data
        val sendMessageChatSessionB = sendMessageSessionStateRefB.single().state.data

        // there are two chat messages on ledge in each node
        val chatMessageStateRefA = nodeA.services.vaultService.queryBy(ChatMessage::class.java).states
        val chatMessageStateRefB = nodeB.services.vaultService.queryBy(ChatMessage::class.java).states
        Assert.assertTrue(chatMessageStateRefA.size == 2)
        Assert.assertTrue(chatMessageStateRefB.size == 2)

        val chatMessageA = chatMessageStateRefA.sortedByDescending { it.state.data.created }.first().state.data
        val chatMessageB = chatMessageStateRefB.sortedByDescending { it.state.data.created }.first().state.data

        // replied chat should be newer than created chat
        val newChatMsgDate = newChatMsg.created
        val sendMessageMsgDate = chatMessageA.created
        Assert.assertTrue(newChatMsgDate < sendMessageMsgDate)

        // same chat id
        Assert.assertEquals(listOf(
                sendMessageChatSessionA.linearId.id.toString(),
                sendMessageChatSessionB.linearId.id.toString(),
                newSessionA.linearId.id.toString(),
                newSessionB.linearId.id.toString(),
                chatMessageA.token.tokenIdentifier,
                chatMessageB.token.tokenIdentifier,
                sendMessage.token.tokenIdentifier
        ).toSet().size,
                1)

        // all chat messages have same message id
        Assert.assertEquals(listOf(
                chatMessageA.linearId,
                chatMessageB.linearId,
                sendMessage.linearId
        ).toSet().size,
                1)

        // same chat in two nodes should have diff participants
        val participantsA = chatMessageA.participants
        val participantsB = chatMessageB.participants
        Assert.assertEquals(participantsA.size, 1)
        Assert.assertEquals(participantsB.size, 1)

        val participantA = participantsA.single()
        val participantB = participantsB.single()
        Assert.assertFalse(participantA.nameOrNull().toString().equals(participantB.nameOrNull().toString()))
    }


    @Test
    fun `send message to chat should follow constrains`() {

        // 1 create one
        val newChatFlow = nodeA.startFlow(CreateSessionFlow(
                subject = "subject",
                content = "content",
                receivers = listOf(nodeB.info.legalIdentities.single())
        ))
        network.runNetwork()
        newChatFlow.getOrThrow()

        val newSessionA = nodeA.services.vaultService.queryBy(ChatSessionInfo::class.java).states.single().state.data
        val newSessionB = nodeB.services.vaultService.queryBy(ChatSessionInfo::class.java).states.single().state.data
        val newChatMessageA = nodeA.services.vaultService.queryBy(ChatMessage::class.java).states.single().state.data
        val newChatMessageB = nodeB.services.vaultService.queryBy(ChatMessage::class.java).states.single().state.data

        // 2 send message to the chat
        val replyFlow = nodeB.startFlow(
                SendMessageFlow(
                        content = "reply content to a chat",
                        chatId = newSessionA.linearId
                )
        )

        network.runNetwork()
        replyFlow.getOrThrow()

        val sendMessageChatSessionA = nodeA.services.vaultService.queryBy(ChatSessionInfo::class.java).states.single().state.data
        val sendMessageChatSessionB = nodeB.services.vaultService.queryBy(ChatSessionInfo::class.java).states.single().state.data

        val chatMessageStateRefA = nodeA.services.vaultService.queryBy(ChatMessage::class.java).states
        val chatMessageStateRefB = nodeB.services.vaultService.queryBy(ChatMessage::class.java).states

        val chatMessageA = chatMessageStateRefA.sortedByDescending { it.state.data.created }.first().state.data
        val chatMessageB = chatMessageStateRefB.sortedByDescending { it.state.data.created }.first().state.data

        // the following tests are based on "state machine" constrains
        // chatId must exist
        Assert.assertEquals(listOf(
                sendMessageChatSessionA.linearId.id.toString(),
                sendMessageChatSessionB.linearId.id.toString(),
                newSessionA.linearId.id.toString(),
                newSessionB.linearId.id.toString(),
                chatMessageA.token.tokenIdentifier,
                chatMessageB.token.tokenIdentifier,
                newChatMessageA.token.tokenIdentifier,
                newChatMessageB.token.tokenIdentifier
        ).toSet().size,
                1)

        // replier must be in existing participants
        Assert.assertTrue(sendMessageChatSessionA.receivers.contains(nodeB.info.legalIdentities.single()))
        Assert.assertTrue(sendMessageChatSessionB.receivers.contains(nodeB.info.legalIdentities.single()))

        //  sender must be the reply initiator
        Assert.assertTrue(chatMessageA.sender.equals(nodeB.info.legalIdentities.single()))
        Assert.assertTrue(chatMessageB.sender.equals(nodeB.info.legalIdentities.single()))

    }

    @Test
    fun `should be possible to send message from other participants to a chat`() {

        // 1 create one
        val newChatFlow = nodeA.startFlow(CreateSessionFlow(
                subject = "subject",
                content = "content",
                receivers = listOf(nodeB.info.legalIdentities.single(), nodeC.info.legalIdentities.single())
        ))
        network.runNetwork()
        val msg = newChatFlow.getOrThrow()
        val chatId = UniqueIdentifier.fromString(msg.state.data.token.tokenIdentifier)

        // 2.1 send message from other party
        val sendFlowB = nodeB.startFlow(
                SendMessageFlow(
                        content = "reply content to a chat",
                        chatId = chatId
                )
        )

        network.runNetwork()
        sendFlowB.getOrThrow()

        // 2.2 send message from other party
        val sendFlowC = nodeC.startFlow(
                SendMessageFlow(
                        content = "reply content to a chat",
                        chatId = chatId
                )
        )

        network.runNetwork()
        sendFlowC.getOrThrow()

        val sessionA = nodeA.services.vaultService.queryBy(ChatSessionInfo::class.java).states
        val sessionB = nodeB.services.vaultService.queryBy(ChatSessionInfo::class.java).states
        val sessionC = nodeB.services.vaultService.queryBy(ChatSessionInfo::class.java).states
        val messageA = nodeA.services.vaultService.queryBy(ChatMessage::class.java).states
        val messageB = nodeB.services.vaultService.queryBy(ChatMessage::class.java).states
        val messageC = nodeB.services.vaultService.queryBy(ChatMessage::class.java).states

        Assert.assertEquals(sessionA.size, 1)
        Assert.assertEquals(sessionB.size, 1)
        Assert.assertEquals(sessionC.size, 1)
        Assert.assertEquals(messageA.size, 3)
        Assert.assertEquals(messageB.size, 3)
        Assert.assertEquals(messageC.size, 3)
    }
}
