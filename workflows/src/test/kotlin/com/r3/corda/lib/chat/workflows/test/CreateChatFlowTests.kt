package com.r3.corda.lib.chat.workflows.test

import com.r3.corda.lib.chat.contracts.states.ChatMessage
import com.r3.corda.lib.chat.contracts.states.ChatMetaInfo
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

class CreateChatFlowTests {

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

        val chatFlow = nodeA.startFlow(CreateChatFlow(
                subject = "subject",
                content = "content",
                receivers = listOf(nodeB.info.legalIdentities.single())
        ))
        network.runNetwork()
        val txn = chatFlow.getOrThrow()

        // message level
        val chatMessageA = nodeA.services.vaultService.queryBy(ChatMessage::class.java).states.single().state.data
        val chatMessageB = nodeB.services.vaultService.queryBy(ChatMessage::class.java).states.single().state.data
        Assert.assertTrue(chatMessageB.linearId == chatMessageA.linearId)
        Assert.assertTrue(chatMessageB.content  == chatMessageA.content)

        // message pointer (meta info) level
        Assert.assertTrue(chatMessageB.token.tokenIdentifier == chatMessageA.token.tokenIdentifier)
        Assert.assertTrue(chatMessageB.holder.owningKey != chatMessageA.holder.owningKey)


        // same chat in two nodes should have diff participants
        val msgPartiesA = chatMessageA.participants
        val msgPartiesB = chatMessageB.participants
        Assert.assertEquals(msgPartiesA.size, 1)
        Assert.assertEquals(msgPartiesB.size, 1)

        val msgPartyA = msgPartiesA.single()
        val msgPartyB = msgPartiesB.single()
        Assert.assertFalse(msgPartyA.nameOrNull().toString().equals(msgPartyB.nameOrNull().toString()))

        //check whether the created one in node B is same as that in the DB of host node A
        val chatMetaA = nodeA.services.vaultService.queryBy(ChatMetaInfo::class.java).states.single().state.data
        val chatMetaB = nodeB.services.vaultService.queryBy(ChatMetaInfo::class.java).states.single().state.data
        Assert.assertTrue(chatMetaB.linearId == chatMetaA.linearId)

        // same chat meta in two nodes should have same participants: admin itself
        val metaPartiesA = chatMetaA.participants
        val metaPartiesB = chatMetaB.participants
        Assert.assertEquals(metaPartiesA.size, 1)
        Assert.assertEquals(metaPartiesB.size, 1)

        Assert.assertTrue(metaPartiesA.subtract(metaPartiesB).isEmpty())
        Assert.assertTrue(metaPartiesB.subtract(metaPartiesA).isEmpty())

    }


}
