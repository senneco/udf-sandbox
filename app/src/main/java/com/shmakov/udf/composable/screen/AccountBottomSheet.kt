package com.shmakov.udf.composable.screen

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import com.shmakov.udf.composable.content.AccountBottomSheetContent
import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.BottomSheet

class AccountBottomSheet(
    override val entry: BackStackEntry,
) : BottomSheet(entry) {

    @Composable
    override fun ColumnScope.Content() {
        val route = entry.route as Account

        AccountBottomSheetContent(
            accountId = route.accountId,
        )
    }
}
