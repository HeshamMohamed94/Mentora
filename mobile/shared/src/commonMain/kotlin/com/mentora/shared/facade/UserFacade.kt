package com.mentora.shared.facade

import com.mentora.shared.domain.usecase.user.GetProfileUseCase
import com.mentora.shared.domain.usecase.user.ObserveLocaleUseCase
import com.mentora.shared.domain.usecase.user.SetLocaleUseCase
import com.mentora.shared.domain.usecase.user.SetThemeUseCase
import com.mentora.shared.domain.usecase.user.UpdateProfileUseCase
import org.koin.core.Koin

/** Task 6's user profile/preferences/locale-sync domain — e.g. `sdk.user.setLocale(AppLocale.Arabic)`. */
class UserFacade internal constructor(koin: Koin) {
    val getProfile: GetProfileUseCase = koin.get()
    val updateProfile: UpdateProfileUseCase = koin.get()
    val observeLocale: ObserveLocaleUseCase = koin.get()
    val setLocale: SetLocaleUseCase = koin.get()
    val setTheme: SetThemeUseCase = koin.get()
}
