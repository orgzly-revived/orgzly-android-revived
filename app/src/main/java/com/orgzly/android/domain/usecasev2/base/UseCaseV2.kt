package com.orgzly.android.domain.usecasev2.base

import com.orgzly.android.data.DataRepository
import com.orgzly.android.usecase.UseCase
import com.orgzly.android.domain.services.DataUpdateService

/**
 * Interface used to semantically group UseCases together. A use case represents a discrete user
 * interaction with a system, intended to encapsulate related business logic.
 *
 * UseCaseV2 is distinguished from [UseCase] by no longer being dependent on [DataRepository],
 * following the more conventional structure of accepting dependencies in the constructor and
 * parameters through an invoke function.
 *
 * For example:
 * ```kotlin
 * class ExampleUseCase @Inject constructor(
 *     private val dataRepository: DataRepository
 *     private val dataUpdateService: DataUpdateService
 * ): UseCaseV2 {
 *     operator fun invoke(parameter: String): ReturnValue {
 *         // Work
 *         dataUpdateService.onLocalDataModified(modificationType = LocalDataModificationType.OTHER)
 *     }
 * }
 * ```
 * Use cases are *currently* expected to inform the app when they have mutated local data through
 * calling [DataUpdateService.onLocalDataModified], and/or
 * [DataUpdateService.onListWidgetUpdateRequired].
 *
 * Furthermore, `invoke` functions may `suspend` and emit `Flow`s (in fact returning a `Flow` is
 * encouraged), however `Flow` returning `invoke`s should not be suspend functions. Rather, they
 * should execute any suspend operations inside a `flow` builder block such as:
 *
 * ```kotlin
 * operator fun invoke(): Flow<ReturnValue> = flow {
 *     val value = suspendingFunction()
 *     emitAll(flowEmittingFunction(value))
 * }
 * ```
 *
 * Finally, use cases must be stateless. Rather than holding state between invocations, state should
 * either be held in the data layer (repository, etc), or in a ViewModel.
 */
interface UseCaseV2
