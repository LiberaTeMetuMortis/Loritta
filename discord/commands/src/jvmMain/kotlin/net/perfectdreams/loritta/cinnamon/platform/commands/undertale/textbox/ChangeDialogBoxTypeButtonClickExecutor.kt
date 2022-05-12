package net.perfectdreams.loritta.cinnamon.platform.commands.undertale.textbox

import net.perfectdreams.discordinteraktions.common.entities.User
import net.perfectdreams.gabrielaimageserver.client.GabrielaImageServerClient
import net.perfectdreams.loritta.cinnamon.platform.LorittaCinnamon
import net.perfectdreams.loritta.cinnamon.platform.commands.ComponentExecutorIds
import net.perfectdreams.loritta.cinnamon.platform.commands.images.gabrielaimageserver.handleExceptions
import net.perfectdreams.loritta.cinnamon.platform.commands.undertale.TextBoxExecutor
import net.perfectdreams.loritta.cinnamon.platform.commands.undertale.TextBoxHelper
import net.perfectdreams.loritta.cinnamon.platform.components.ButtonClickExecutorDeclaration
import net.perfectdreams.loritta.cinnamon.platform.components.ButtonClickWithDataExecutor
import net.perfectdreams.loritta.cinnamon.platform.components.ComponentContext

class ChangeDialogBoxTypeButtonClickExecutor(
    val loritta: LorittaCinnamon,
    val client: GabrielaImageServerClient
) : ButtonClickWithDataExecutor {
    companion object : ButtonClickExecutorDeclaration(ComponentExecutorIds.CHANGE_DIALOG_BOX_TYPE_BUTTON_EXECUTOR)

    override suspend fun onClick(user: User, context: ComponentContext, data: String) {
        // We will already defer to avoid issues
        // Also because we want to edit the message with a file... later!
        context.deferUpdateMessage()

        val (_, type, interactionDataId) = context.decodeDataFromComponentAndRequireUserToMatch<SelectDialogBoxTypeData>(data)

        val textBoxOptionsData = TextBoxHelper.getInteractionDataAndFailIfItDoesNotExist(context, interactionDataId)

        val newData = when (textBoxOptionsData) {
            is TextBoxWithCustomPortraitOptionsData -> textBoxOptionsData.copy(dialogBoxType = type)
            is TextBoxWithGamePortraitOptionsData -> textBoxOptionsData.copy(dialogBoxType = type)
            is TextBoxWithNoPortraitOptionsData -> textBoxOptionsData.copy(dialogBoxType = type)
        }

        // Delete the old interaction data ID from the database, the "createMessage" will create a new one anyways :)
        context.loritta.services.interactionsData.deleteInteractionData(interactionDataId)

        val builtMessage = TextBoxExecutor.createMessage(
            context.loritta,
            context.user,
            context.i18nContext,
            newData
        )

        val dialogBox = client.handleExceptions(context) { TextBoxExecutor.createDialogBox(client, newData) }

        context.updateMessage {
            attachments = mutableListOf() // Remove all attachments from the message!
            addFile("undertale_box.gif", dialogBox)
            apply(builtMessage)
        }
    }
}