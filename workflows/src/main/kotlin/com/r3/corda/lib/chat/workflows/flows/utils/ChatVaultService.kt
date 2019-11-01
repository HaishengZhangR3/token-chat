package com.r3.corda.lib.chat.workflows.flows.utils

import com.r3.corda.lib.chat.contracts.internal.schemas.PersistentChatSessionInfo
import com.r3.corda.lib.chat.contracts.states.ChatMessage
import com.r3.corda.lib.chat.contracts.states.ChatSessionInfo
import com.r3.corda.lib.chat.workflows.flows.service.ChatStatus
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.Vault.StateStatus
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.serialization.SingletonSerializeAsToken
import java.util.*

@CordaService
class ChatVaultService(val serviceHub: AppServiceHub) : SingletonSerializeAsToken() {

    /*  find notary */
    fun notary() = serviceHub.networkMapCache.notaryIdentities.first()
    fun notary(chatId: UniqueIdentifier) = getSessionInfo(chatId).state.notary

    /* get all chats level information */
    // get ID for all chats
    fun getAllChatIDs(status: StateStatus = StateStatus.UNCONSUMED): List<UniqueIdentifier> {

        val idGroup = builder { PersistentChatSessionInfo::created.min(groupByColumns = listOf(PersistentChatSessionInfo::identifier)) }
        val idGroupCriteria = QueryCriteria.VaultCustomQueryCriteria(idGroup)
        val chatInfos = serviceHub.vaultService.queryBy<ChatSessionInfo>(
                criteria = QueryCriteria.LinearStateQueryCriteria(status = status).and(idGroupCriteria)
        )

        return chatInfos.otherResults.filterIsInstance<UUID>().distinct().map { UniqueIdentifier(id = it) }
    }

    // get all chat all messages
    fun getAllMessages(status: StateStatus = StateStatus.UNCONSUMED): List<StateAndRef<ChatMessage>> =
            serviceHub.vaultService.queryBy<ChatMessage>(
                    criteria = QueryCriteria.VaultQueryCriteria(status = status)
            ).states

    /* get chat level message information */
    fun getChatAllMessagesByChatId(chatId: UniqueIdentifier): List<StateAndRef<ChatMessage>>  {
        val allMessages = getAllMessages(StateStatus.ALL)
        return allMessages.filter { it.state.data.token.tokenIdentifier.equals(chatId.id.toString()) }
    }
    fun getChatActiveMessagesByChatId(chatId: UniqueIdentifier): List<StateAndRef<ChatMessage>>  {
        val allMessages = getAllMessages(StateStatus.UNCONSUMED)
        return allMessages.filter { it.state.data.token.tokenIdentifier.equals(chatId.id.toString()) }
    }


    inline fun <reified T : LinearState> getVaultStates(chatId: UniqueIdentifier, status: StateStatus = StateStatus.UNCONSUMED): List<StateAndRef<T>> {
        val stateAndRefs = serviceHub.vaultService.queryBy<T>(
                criteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(chatId), status = status))
        return stateAndRefs.states
    }

    /* get chat level session data information */
    fun getSessionInfo(chatId: UniqueIdentifier): StateAndRef<ChatSessionInfo> = getVaultStates<ChatSessionInfo>(chatId).first()
    fun getActiveSessionInfo(chatId: UniqueIdentifier): StateAndRef<ChatSessionInfo> = getVaultStates<ChatSessionInfo>(chatId).first()
    fun getSessionInfoOrNull(chatId: UniqueIdentifier): StateAndRef<ChatSessionInfo>? = getVaultStates<ChatSessionInfo>(chatId).firstOrNull()

    // get current chat status
    fun getChatStatus(chatId: UniqueIdentifier): ChatStatus {
        val sessionInfo = getSessionInfoOrNull(chatId)
        when (sessionInfo) {
            null -> return ChatStatus.CLOSED
            else -> return ChatStatus.ACTIVE
        }
    }
}
