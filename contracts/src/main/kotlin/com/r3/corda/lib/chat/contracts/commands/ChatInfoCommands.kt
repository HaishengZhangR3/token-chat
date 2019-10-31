package com.r3.corda.lib.chat.contracts.commands

import com.r3.corda.lib.tokens.contracts.commands.EvolvableTokenTypeCommand
import net.corda.core.contracts.CommandData

interface ChatCommand : CommandData

/* Commands below are used in Chat SDK internal */
// Command for basic chat
class CreateMeta : EvolvableTokenTypeCommand
class CreateMessage : ChatCommand

class CloseMeta : EvolvableTokenTypeCommand
class CloseMessages : ChatCommand
