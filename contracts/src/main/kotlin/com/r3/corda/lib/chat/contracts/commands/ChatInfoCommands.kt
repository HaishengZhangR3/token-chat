package com.r3.corda.lib.chat.contracts.commands

import com.r3.corda.lib.tokens.contracts.commands.EvolvableTokenTypeCommand
import net.corda.core.contracts.CommandData

interface ChatCommand : CommandData

/* Commands below are used in Chat SDK internal */
// Command for basic chat
class CreateMeta : EvolvableTokenTypeCommand // it is not needed, instead "Create : EvolvableTokenTypeCommand"
class CreateMessage : ChatCommand // it is not needed, instead "IssueTokenCommand : TokenCommand"

class CloseMeta : EvolvableTokenTypeCommand  // will close meta manually by us instead of redeem since they are different.
class CloseMessages : ChatCommand
