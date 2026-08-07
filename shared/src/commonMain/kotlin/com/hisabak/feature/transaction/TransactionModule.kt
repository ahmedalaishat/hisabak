package com.hisabak.feature.transaction

import com.hisabak.feature.transaction.data.RoomTransactionRepository
import com.hisabak.feature.transaction.domain.TransactionId
import com.hisabak.feature.transaction.domain.TransactionRepository
import com.hisabak.feature.transaction.domain.usecase.CreateTransactionUseCase
import com.hisabak.feature.transaction.domain.usecase.DeleteTransactionUseCase
import com.hisabak.feature.transaction.domain.usecase.GetTransactionsPageUseCase
import com.hisabak.feature.transaction.domain.usecase.ObserveTransactionsUseCase
import com.hisabak.feature.transaction.domain.usecase.ReassignBrandTransactionsUseCase
import com.hisabak.feature.transaction.domain.usecase.UpdateTransactionUseCase
import com.hisabak.feature.transaction.presentation.edit.TransactionDraftBus
import com.hisabak.feature.transaction.presentation.edit.TransactionEditViewModel
import com.hisabak.feature.transaction.presentation.list.TransactionListFilterBus
import com.hisabak.feature.transaction.presentation.list.TransactionListViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val transactionModule = module {
    single<TransactionRepository> { RoomTransactionRepository(dao = get()) }
    single { TransactionListFilterBus() }
    single { TransactionDraftBus() }

    factory { ObserveTransactionsUseCase(get()) }
    factory { GetTransactionsPageUseCase(get()) }
    factory { CreateTransactionUseCase(get(), get()) }
    factory { UpdateTransactionUseCase(get()) }
    factory { DeleteTransactionUseCase(get(), get()) }
    factory { ReassignBrandTransactionsUseCase(get()) }

    viewModel {
        TransactionListViewModel(
            observeTransactions = get(),
            observeBrands = get(),
            observeCategories = get(),
            clock = get(),
            filterBus = get(),
        )
    }

    viewModel { (transactionId: TransactionId?) ->
        TransactionEditViewModel(
            transactionId = transactionId,
            currency = get(),
            clock = get(),
            transactionRepository = get(),
            observeBrands = get(),
            observeCategories = get(),
            createTransaction = get(),
            updateTransaction = get(),
            deleteTransaction = get(),
            draftBus = get(),
            brandCreatedBus = get(),
            analytics = get(),
        )
    }
}
