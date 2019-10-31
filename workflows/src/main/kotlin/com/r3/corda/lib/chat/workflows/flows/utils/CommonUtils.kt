package com.r3.corda.lib.chat.workflows.flows.utils

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import java.util.*


val FlowLogic<*>.chatVaultService get() = serviceHub.cordaService(ChatVaultService::class.java)


fun randomID() = UniqueIdentifier.fromString(UUID.randomUUID().toString())