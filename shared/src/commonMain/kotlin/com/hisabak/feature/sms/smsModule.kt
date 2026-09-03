package com.hisabak.feature.sms

import com.hisabak.feature.sms.data.RoomSmsRepository
import com.hisabak.feature.sms.data.RoomSmsTemplateRepository
import com.hisabak.feature.sms.data.parser.ObservingSmsTemplateDetector
import com.hisabak.feature.sms.data.parser.TemplateSmsParser
import com.hisabak.feature.sms.domain.SmsParser
import com.hisabak.feature.sms.domain.SmsRepository
import com.hisabak.feature.sms.domain.SmsTemplateDetector
import com.hisabak.feature.sms.domain.SmsTemplateRepository
import com.hisabak.feature.sms.domain.SmsTransactionProcessor
import com.hisabak.di.APPLICATION_SCOPE
import com.hisabak.feature.sms.domain.ai.AutoConfirmSuggestionUseCase
import com.hisabak.feature.sms.domain.ai.ConfirmAiSuggestionUseCase
import com.hisabak.feature.sms.domain.ai.DismissAiSuggestionUseCase
import com.hisabak.feature.sms.domain.ai.SuggestAiParseUseCase
import com.hisabak.feature.sms.domain.capture.CaptureTransactionUseCase
import com.hisabak.feature.sms.domain.SmsMessageId
import com.hisabak.feature.sms.domain.SmsTemplateId
import com.hisabak.feature.sms.domain.template.DeleteSmsTemplateUseCase
import com.hisabak.feature.sms.domain.template.ObserveSmsTemplatesUseCase
import com.hisabak.feature.sms.domain.template.PreviewSmsTemplateUseCase
import com.hisabak.feature.sms.domain.template.SaveSmsTemplateUseCase
import com.hisabak.feature.sms.domain.template.SetSmsTemplateEnabledUseCase
import com.hisabak.feature.sms.domain.template.SynthesizeTemplateUseCase
import com.hisabak.feature.sms.domain.usecase.DeleteSmsUseCase
import com.hisabak.feature.sms.domain.usecase.ImportParsedSmsUseCase
import com.hisabak.feature.sms.domain.usecase.MarkSmsReviewedUseCase
import com.hisabak.feature.sms.domain.usecase.IngestSmsUseCase
import com.hisabak.feature.sms.domain.usecase.ObserveSmsMessagesUseCase
import com.hisabak.feature.sms.domain.usecase.ReparseSmsMessageUseCase
import com.hisabak.feature.sms.presentation.inbox.SmsInboxViewModel
import com.hisabak.feature.sms.presentation.templates.SmsTemplateEditViewModel
import com.hisabak.feature.sms.presentation.templates.SmsTemplatesViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val smsModule = module {
    single { com.hisabak.feature.sms.presentation.InboxOpenBus() }
    single<SmsRepository> { RoomSmsRepository(dao = get()) }

    single<SmsTemplateRepository> { RoomSmsTemplateRepository(dao = get(), clock = get()) }
    single<SmsTemplateDetector> {
        ObservingSmsTemplateDetector(repository = get(), scope = get(APPLICATION_SCOPE))
    }
    single<SmsParser> { TemplateSmsParser(defaultCurrency = get()) }

    single {
        SmsTransactionProcessor(
            detector = get(),
            parser = get(),
            findOrCreateBrand = get(),
            transactionRepository = get(),
            smsRepository = get(),
            clock = get(),
        )
    }

    factory { ObserveSmsMessagesUseCase(get()) }
    factory {
        IngestSmsUseCase(
            smsRepository = get(),
            processor = get(),
            clock = get(),
            suggestAiParse = get(),
            autoConfirmSuggestion = get<AutoConfirmSuggestionUseCase>()::invoke,
            appScope = get(APPLICATION_SCOPE),
        )
    }
    factory {
        SuggestAiParseUseCase(
            aiParser = get(),
            smsRepository = get(),
            brandRepository = get(),
            defaultCurrency = get(),
            clock = get(),
            analytics = get(),
        )
    }
    factory {
        ConfirmAiSuggestionUseCase(
            smsRepository = get(),
            processor = get(),
            limitMonitor = get(),
            synthesizeTemplate = get(),
            learnBrandAlias = get(),
            analytics = get(),
        )
    }
    factory { DismissAiSuggestionUseCase(smsRepository = get(), analytics = get()) }
    factory {
        AutoConfirmSuggestionUseCase(
            preferences = get(),
            resolveBrand = get(),
            confirm = get(),
            recordedNotifier = get(),
        )
    }
    factory {
        CaptureTransactionUseCase(
            ingest = get(),
            recordedNotifier = get(),
            limitMonitor = get(),
            analytics = get(),
        )
    }
    factory { DeleteSmsUseCase(get()) }
    factory { MarkSmsReviewedUseCase(smsRepository = get(), analytics = get()) }
    factory {
        ImportParsedSmsUseCase(
            smsRepository = get(),
            processor = get(),
            limitMonitor = get(),
            analytics = get(),
        )
    }
    factory {
        ReparseSmsMessageUseCase(
            smsRepository = get(),
            templateRepository = get(),
            parser = get(),
            processor = get(),
        )
    }

    factory { ObserveSmsTemplatesUseCase(get()) }
    factory { SaveSmsTemplateUseCase(repository = get(), clock = get(), analytics = get()) }
    factory { DeleteSmsTemplateUseCase(repository = get(), analytics = get()) }
    factory { SetSmsTemplateEnabledUseCase(repository = get(), analytics = get()) }
    factory { PreviewSmsTemplateUseCase(smsRepository = get()) }
    factory {
        SynthesizeTemplateUseCase(
            repository = get(),
            saveTemplate = get(),
            previewTemplate = get(),
            clock = get(),
            analytics = get(),
        )
    }

    viewModel {
        SmsTemplatesViewModel(
            observeTemplates = get(),
            setEnabled = get(),
            deleteTemplate = get(),
        )
    }

    viewModel { (templateId: SmsTemplateId?, sampleSmsId: SmsMessageId?) ->
        SmsTemplateEditViewModel(
            templateId = templateId,
            sampleSmsId = sampleSmsId,
            templateRepository = get(),
            smsRepository = get(),
            saveTemplate = get(),
            previewTemplate = get(),
            reparseSms = get(),
            setTemplateEnabled = get(),
        )
    }

    viewModel {
        SmsInboxViewModel(
            observeMessages = get(),
            capture = get(),
            importParsed = get(),
            deleteSms = get(),
            detector = get(),
            parser = get(),
            aiParser = get(),
            markReviewed = get(),
            suggestAiParse = get(),
            confirmAiSuggestion = get(),
            dismissAiSuggestion = get(),
            deleteTemplate = get(),
            preferences = get(),
            appConfig = get(),
            analytics = get(),
        )
    }
}
