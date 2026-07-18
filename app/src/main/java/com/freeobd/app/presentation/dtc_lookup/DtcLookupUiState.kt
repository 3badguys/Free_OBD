/*
 * Copyright 2026 3badguys <chuiC456@163.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.freeobd.app.presentation.dtc_lookup

import com.freeobd.app.data.local.entity.DtcEntity

/** Available page size options for DTC lookup. */
val DTC_PAGE_SIZE_OPTIONS = listOf(10, 20, 50, 100, 200, 500)

/** Default page size. */
const val DTC_DEFAULT_PAGE_SIZE = 50

/**
 * UI state for the DTC Lookup reference page.
 *
 * @property items        DTC entities for the current page.
 * @property currentPage  1-based page index.
 * @property totalPages   Total number of pages (0 if no results).
 * @property totalItems   Total number of matching items.
 * @property pageSize     Number of items displayed per page.
 * @property searchQuery  Current search input (empty = browse all).
 * @property isLoading    True while fetching a page.
 */
data class DtcLookupUiState(
    val items: List<DtcEntity> = emptyList(),
    val currentPage: Int = 1,
    val totalPages: Int = 0,
    val totalItems: Int = 0,
    val pageSize: Int = DTC_DEFAULT_PAGE_SIZE,
    val searchQuery: String = "",
    val isLoading: Boolean = true
)

sealed interface DtcLookupEvent {
    /** Load the first page (for initial load or after a search reset). */
    data object Load : DtcLookupEvent

    /** User typed a search query. Debounced in ViewModel. */
    data class Search(val query: String) : DtcLookupEvent

    /** Change the number of items displayed per page. */
    data class SetPageSize(val size: Int) : DtcLookupEvent

    /** Jump to a specific page number (1-based). */
    data class GoToPage(val page: Int) : DtcLookupEvent

    /** Go to the next page. */
    data object NextPage : DtcLookupEvent

    /** Go to the previous page. */
    data object PrevPage : DtcLookupEvent

    /** Show detail dialog for a specific code. */
    data class ShowDetail(val entity: DtcEntity) : DtcLookupEvent

    /** Clear the selected detail dialog. */
    data object DismissDetail : DtcLookupEvent
}
