package com.hisabak.feature.category

import com.hisabak.feature.category.data.RoomCategoryLimitRepository
import com.hisabak.feature.category.data.RoomCategoryRepository
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.CategoryLimitRepository
import com.hisabak.feature.category.domain.CategoryRepository
import com.hisabak.feature.category.domain.usecase.CreateCategoryUseCase
import com.hisabak.feature.category.domain.usecase.DeleteCategoryUseCase
import com.hisabak.feature.category.domain.usecase.ObserveCategoriesUseCase
import com.hisabak.feature.category.domain.usecase.ObserveCategoryLimitsUseCase
import com.hisabak.feature.category.domain.usecase.SetCategoryLimitUseCase
import com.hisabak.feature.category.domain.usecase.UpdateCategoryUseCase
import com.hisabak.feature.category.presentation.CategoryCreatedBus
import com.hisabak.feature.category.presentation.edit.CategoryEditPrefill
import com.hisabak.feature.category.presentation.edit.CategoryEditViewModel
import com.hisabak.feature.category.presentation.list.CategoryListViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val categoryModule = module {
    single<CategoryRepository> { RoomCategoryRepository(dao = get()) }
    single<CategoryLimitRepository> { RoomCategoryLimitRepository(dao = get(), currency = get()) }
    single { CategoryCreatedBus() }

    factory { ObserveCategoriesUseCase(get()) }
    factory { CreateCategoryUseCase(get()) }
    factory { UpdateCategoryUseCase(get()) }
    factory { DeleteCategoryUseCase(get()) }
    factory { ObserveCategoryLimitsUseCase(get()) }
    factory { SetCategoryLimitUseCase(repository = get(), clock = get()) }

    viewModel {
        CategoryListViewModel(
            observeCategories = get(),
            observeBrands = get(),
            observeTransactions = get(),
            deleteCategory = get(),
        )
    }

    viewModel { (categoryId: CategoryId?, prefill: CategoryEditPrefill?) ->
        CategoryEditViewModel(
            categoryId = categoryId,
            prefill = prefill,
            categoryRepository = get(),
            createCategory = get(),
            updateCategory = get(),
            observeCategoryLimits = get(),
            setCategoryLimit = get(),
            currency = get(),
            clock = get(),
            analytics = get(),
        )
    }
}
