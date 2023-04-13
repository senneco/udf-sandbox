package com.shmakov.udf.composable.screen

import androidx.compose.runtime.Composable
import com.shmakov.udf.composable.content.AccountBottomSheetContent
import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.ModalScreen
import com.shmakov.udf.navigation.ModalScreenState
import com.shmakov.udf.navigation.NavState

class AccountBottomSheet(
    override val destination: Account
) : ModalScreen(destination) {

    @Composable
    override fun Content(
        nestedNavState: NavState,
        targetState: ModalScreenState,
        onHide: () -> Unit,
    ) {
        AccountBottomSheetContent(
            accountId = destination.accountId,
            targetState = targetState,
            onHide = onHide,
        )
    }
}