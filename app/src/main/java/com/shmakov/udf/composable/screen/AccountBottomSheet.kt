package com.shmakov.udf.composable.screen

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import com.shmakov.udf.composable.content.AccountBottomSheetContent
import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.BottomSheet

class AccountBottomSheet(
    override val destination: Account
) : BottomSheet(destination) {

    @Composable
    override fun ColumnScope.Content() {
        AccountBottomSheetContent(
            accountId = destination.accountId,
        )
    }
}