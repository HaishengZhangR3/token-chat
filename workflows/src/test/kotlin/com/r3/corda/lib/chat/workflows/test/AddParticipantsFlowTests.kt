package com.r3.corda.lib.chat.workflows.test

import com.r3.corda.lib.chat.contracts.states.ChatSessionInfo
import com.r3.corda.lib.chat.workflows.flows.AddParticipantsFlow
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

class AddParticipantsFlowTests {

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
    fun `should be possible to add participants to a chat`() {

        // 1 create one
        val newChatFlow = nodeA.startFlow(CreateSessionFlow(
                subject = "subject",
                content = "content",
                receivers = listOf(nodeB.info.legalIdentities.single())
        ))
        network.runNetwork()
        newChatFlow.getOrThrow()

        val oldSessionA = nodeA.services.vaultService.queryBy(ChatSessionInfo::class.java).states.single().state.data
        val oldSessionB = nodeB.services.vaultService.queryBy(ChatSessionInfo::class.java).states.single().state.data

        // 2. add new participants
        val addParticipantsFlow = nodeA.startFlow(
                AddParticipantsFlow(
                        toAdd = listOf(nodeC.info.legalIdentities.single()),
                        chatId = oldSessionA.linearId
                )
        )

        network.runNetwork()
        addParticipantsFlow.getOrThrow()

        // old SessionInfo is consumed in A,B, and new one should be in A,B and C
        val sessionA = nodeA.services.vaultService.queryBy(ChatSessionInfo::class.java).states.single().state.data
        val sessionB = nodeB.services.vaultService.queryBy(ChatSessionInfo::class.java).states.single().state.data
        val sessionC = nodeC.services.vaultService.queryBy(ChatSessionInfo::class.java).states.single().state.data

        // make sure all of the receivers for all of the nodes are same
        Assert.assertTrue((sessionA.receivers - sessionB.receivers).isEmpty())
        Assert.assertTrue((sessionB.receivers - sessionA.receivers).isEmpty())
        Assert.assertTrue((sessionB.receivers - sessionC.receivers).isEmpty())
        Assert.assertTrue((sessionC.receivers - sessionB.receivers).isEmpty())

        val expectedParticipants = oldSessionA.receivers + nodeC.info.legalIdentities.single()
        Assert.assertTrue((sessionC.receivers - expectedParticipants).isEmpty())
        Assert.assertTrue((expectedParticipants - sessionC.receivers).isEmpty())

        Assert.assertEquals(
                listOf(sessionA.linearId, sessionB.linearId, sessionC.linearId,
                        oldSessionA.linearId, oldSessionB.linearId).distinct().size,
                1)
    }

    @Test
    fun `add participants should follow constrain`() {

        // 1 create one
        val newChatFlow = nodeA.startFlow(CreateSessionFlow(
                subject = "subject",
                content = "content",
                receivers = listOf(nodeB.info.legalIdentities.single())
        ))
        network.runNetwork()
        newChatFlow.getOrThrow()

        val oldSessionA = nodeA.services.vaultService.queryBy(ChatSessionInfo::class.java).states.single().state.data
        val oldSessionB = nodeB.services.vaultService.queryBy(ChatSessionInfo::class.java).states.single().state.data

        // 2. add new participants
        val addParticipantsFlow = nodeA.startFlow(
                AddParticipantsFlow(
                        toAdd = listOf(nodeC.info.legalIdentities.single()),
                        chatId = oldSessionA.linearId
                )
        )

        network.runNetwork()
        addParticipantsFlow.getOrThrow()

        // old SessionInfo is consumed in A,B, and new one should be in A,B and C
        val sessionA = nodeA.services.vaultService.queryBy(ChatSessionInfo::class.java).states.single().state.data
        val sessionB = nodeB.services.vaultService.queryBy(ChatSessionInfo::class.java).states.single().state.data
        val sessionC = nodeC.services.vaultService.queryBy(ChatSessionInfo::class.java).states.single().state.data


        // the following tests are based on "state machine" constrains
        // linearId and subject must not change
        Assert.assertEquals(
                listOf(sessionA.linearId, sessionB.linearId, sessionC.linearId,
                        oldSessionA.linearId, oldSessionB.linearId).distinct().size,
                1)
        Assert.assertEquals(
                listOf(sessionA.subject, sessionB.subject, sessionC.subject,
                        oldSessionA.subject, oldSessionB.subject).distinct().size,
                1)

        // admin must not change
        val admins = listOf(
                sessionA.admin,
                sessionB.admin,
                sessionC.admin,
                oldSessionA.admin,
                oldSessionB.admin
        ).distinct()
        Assert.assertEquals(admins.size, 1)

        // admin must be the initiator
        Assert.assertEquals(nodeA.info.legalIdentities.single(), admins.single())

        // receivers list must change to new participants
        val expectedParticipants = oldSessionA.receivers + nodeC.info.legalIdentities.single()
        Assert.assertEquals((sessionC.receivers - expectedParticipants).size, 0)
        Assert.assertEquals((expectedParticipants - sessionC.receivers).size, 0)
        Assert.assertEquals(sessionA.receivers, sessionB.receivers)
        Assert.assertEquals(sessionA.receivers, sessionC.receivers)
    }
}
