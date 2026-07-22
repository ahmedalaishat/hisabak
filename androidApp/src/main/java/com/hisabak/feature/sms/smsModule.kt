package com.hisabak.feature.sms

import com.hisabak.feature.sms.data.RoomSmsRepository
import com.hisabak.feature.sms.data.parser.RegexSmsTemplateDetector
import com.hisabak.feature.sms.data.parser.TemplateSmsParser
import com.hisabak.feature.sms.domain.DefaultSmsTemplates
import com.hisabak.feature.sms.domain.SmsParser
import com.hisabak.feature.sms.domain.SmsRepository
import com.hisabak.feature.sms.domain.SmsTemplateDetector
import com.hisabak.feature.sms.domain.SmsTransactionProcessor
import com.hisabak.feature.sms.domain.capture.CaptureTransactionUseCase
import com.hisabak.feature.sms.domain.usecase.DeleteSmsUseCase
import com.hisabak.feature.sms.domain.usecase.IngestSmsUseCase
import com.hisabak.feature.sms.domain.usecase.ObserveSmsMessagesUseCase
import com.hisabak.feature.sms.presentation.inbox.SmsInboxViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val smsModule = module {
    single<SmsRepository> { RoomSmsRepository(dao = get()) }

    single<SmsTemplateDetector> { RegexSmsTemplateDetector(DefaultSmsTemplates.patterns) }
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
    factory { IngestSmsUseCase(smsRepository = get(), processor = get(), clock = get()) }
    factory {
        CaptureTransactionUseCase(
            ingest = get(),
            recordedNotifier = get(),
            limitMonitor = get(),
            analytics = get(),
        )
    }
    factory { DeleteSmsUseCase(get()) }

    viewModel {
        SmsInboxViewModel(
            observeMessages = get(),
            capture = get(),
            deleteSms = get(),
            detector = get(),
            parser = get(),
        )
    }
}
