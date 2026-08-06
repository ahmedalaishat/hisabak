package com.hisabak.feature.brand

import com.hisabak.feature.brand.data.RoomBrandRepository
import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.brand.domain.BrandRepository
import com.hisabak.feature.brand.domain.usecase.CreateBrandUseCase
import com.hisabak.feature.brand.domain.usecase.DeleteBrandUseCase
import com.hisabak.feature.brand.domain.usecase.FindOrCreateBrandUseCase
import com.hisabak.feature.brand.domain.usecase.ObserveBrandsUseCase
import com.hisabak.feature.brand.domain.usecase.UpdateBrandUseCase
import com.hisabak.feature.brand.presentation.BrandEditBus
import com.hisabak.feature.brand.presentation.edit.BrandEditViewModel
import com.hisabak.feature.brand.presentation.list.BrandListViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val brandModule = module {
    single<BrandRepository> { RoomBrandRepository(dao = get(), transactionDao = get()) }
    single { BrandEditBus() }

    factory { ObserveBrandsUseCase(get()) }
    factory { CreateBrandUseCase(get()) }
    factory { UpdateBrandUseCase(get()) }
    factory { DeleteBrandUseCase(get()) }
    factory { FindOrCreateBrandUseCase(get()) }

    viewModel {
        BrandListViewModel(
            observeBrands = get(),
            observeCategories = get(),
            observeTransactions = get(),
            deleteBrand = get(),
            reassignBrandTransactions = get(),
            analytics = get(),
        )
    }

    viewModel { (brandId: BrandId?) ->
        BrandEditViewModel(
            brandId = brandId,
            brandRepository = get(),
            observeCategories = get(),
            createBrand = get(),
            updateBrand = get(),
            categoryCreatedBus = get(),
            analytics = get(),
        )
    }
}
