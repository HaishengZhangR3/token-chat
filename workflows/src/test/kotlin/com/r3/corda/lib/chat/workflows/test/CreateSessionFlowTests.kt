package com.r3.corda.lib.chat.workflows.test

import com.r3.corda.lib.chat.contracts.states.ChatMessage
import com.r3.corda.lib.chat.contracts.states.ChatSessionInfo
import com.r3.corda.lib.chat.workflows.flows.CreateSessionFlow
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

class CreateSessionFlowTests {

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
        val chatFlow = nodeA.startFlow(CreateSessionFlow(
                subject = "subject",
                content = "content",
                receivers = listOf(nodeB.info.legalIdentities.single())
        ))
        network.runNetwork()
        val txnMessage = chatFlow.getOrThrow().state.data

        // message level
        val chatMessageA = nodeA.services.vaultService.queryBy(ChatMessage::class.java).states.single().state.data
        val chatMessageB = nodeB.services.vaultService.queryBy(ChatMessage::class.java).states.single().state.data
        Assert.assertTrue(chatMessageB.linearId == chatMessageA.linearId)
        Assert.assertEquals(txnMessage.linearId, chatMessageB.linearId)

        Assert.assertTrue(chatMessageB.content  == chatMessageA.content)
        Assert.assertTrue(txnMessage.content  == chatMessageA.content)

        // message pointer (session info) level
        Assert.assertTrue(chatMessageB.token.tokenIdentifier == chatMessageA.token.tokenIdentifier)
        Assert.assertTrue(txnMessage.token.tokenIdentifier == chatMessageA.token.tokenIdentifier)

        Assert.assertTrue(chatMessageB.holder.owningKey != chatMessageA.holder.owningKey)
        Assert.assertTrue(txnMessage.holder.owningKey != chatMessageA.holder.owningKey)

        // same chat in two nodes should have diff participants
        val msgPartiesA = chatMessageA.participants
        val msgPartiesB = chatMessageB.participants
        Assert.assertEquals(msgPartiesA.size, 1)
        Assert.assertEquals(msgPartiesB.size, 1)

        val msgPartyA = msgPartiesA.single()
        val msgPartyB = msgPartiesB.single()
        Assert.assertFalse(msgPartyA.nameOrNull().toString().equals(msgPartyB.nameOrNull().toString()))

        //check whether the session info in node B is same as in node A
        val sessionA = nodeA.services.vaultService.queryBy(ChatSessionInfo::class.java).states.single().state.data
        val sessionB = nodeB.services.vaultService.queryBy(ChatSessionInfo::class.java).states.single().state.data
        Assert.assertTrue(sessionB.linearId == sessionA.linearId)
        Assert.assertTrue(chatMessageB.token.tokenIdentifier == sessionA.linearId.toString())

        // same chat session in two nodes should have same participants
        val partiesA = sessionA.participants
        val partiesB = sessionB.participants
        Assert.assertEquals(partiesA.size, 2)
        Assert.assertEquals(partiesB.size, 2)

        Assert.assertTrue(partiesA.subtract(partiesB).isEmpty())
        Assert.assertTrue(partiesB.subtract(partiesA).isEmpty())

    }

    @Test
    fun `create chat should follow constrains`() {
        val chatFlow = nodeA.startFlow(CreateSessionFlow(
                subject = "subject",
                content = "content",
                receivers = listOf(nodeB.info.legalIdentities.single())
        ))
        network.runNetwork()
        chatFlow.getOrThrow()

        val chatMessageA = nodeA.services.vaultService.queryBy(ChatMessage::class.java).states.single().state.data
        val chatMessageB = nodeB.services.vaultService.queryBy(ChatMessage::class.java).states.single().state.data

        val sessionA = nodeA.services.vaultService.queryBy(ChatSessionInfo::class.java).states.single().state.data
        val sessionB = nodeB.services.vaultService.queryBy(ChatSessionInfo::class.java).states.single().state.data


        // the following tests are based on "state machine" constrains
        // chat admin must be the chat initiator
        Assert.assertTrue(sessionA.admin.equals(nodeA.info.legalIdentities.single()))
        Assert.assertTrue(sessionB.admin.equals(nodeA.info.legalIdentities.single()))

        // chatId/linearId must not exist
        // we're using UUID auto generated mechanism, so we're sure it'd be unique

        // chatId/linearId in two states must be same
        Assert.assertTrue(sessionB.linearId == sessionA.linearId)
        Assert.assertTrue(chatMessageB.token.tokenIdentifier == chatMessageA.token.tokenIdentifier)
        Assert.assertTrue(chatMessageB.token.tokenIdentifier == sessionA.linearId.toString())
        Assert.assertTrue(sessionB.linearId == sessionA.linearId)

        // sender must be the chat initiator
        Assert.assertTrue(chatMessageA.sender == nodeA.info.legalIdentities.single())
        Assert.assertTrue(chatMessageB.sender == nodeA.info.legalIdentities.single())

        // participants in ChatMessage only include the party receiving the message
        Assert.assertTrue(chatMessageA.participants.single() == nodeA.info.legalIdentities.single())
        Assert.assertTrue(chatMessageB.participants.single() == nodeB.info.legalIdentities.single())

        // participants in session participants include both admin and receivers
        val allParticipants = listOf(nodeA.info.legalIdentities.single(), nodeB.info.legalIdentities.single())
        val partiesA = sessionA.participants
        val partiesB = sessionB.participants
        Assert.assertTrue(partiesA.subtract(allParticipants).isEmpty())
        Assert.assertTrue(partiesB.subtract(allParticipants).isEmpty())
    }
}
