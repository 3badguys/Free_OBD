package com.freeobd.app.presentation.dtc_lookup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeobd.app.data.local.dao.DtcDao
import com.freeobd.app.data.local.entity.DtcEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DtcLookupViewModel(
    private val dtcDao: DtcDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(DtcLookupUiState())
    val uiState: StateFlow<DtcLookupUiState> = _uiState.asStateFlow()

    /** Selected DTC for detail dialog display. */
    private val _selectedEntity = MutableStateFlow<DtcEntity?>(null)
    val selectedEntity: StateFlow<DtcEntity?> = _selectedEntity.asStateFlow()

    /** Debounce job for search input. */
    private var searchJob: Job? = null

    fun onEvent(event: DtcLookupEvent) {
        when (event) {
            DtcLookupEvent.Load -> loadPage(1)
            is DtcLookupEvent.Search -> onSearch(event.query)
            is DtcLookupEvent.SetPageSize -> {
                _uiState.value = _uiState.value.copy(pageSize = event.size)
                loadPage(1)
            }
            is DtcLookupEvent.GoToPage -> {
                val page = event.page.coerceIn(1, _uiState.value.totalPages)
                loadPage(page)
            }
            DtcLookupEvent.NextPage -> {
                val next = _uiState.value.currentPage + 1
                if (next <= _uiState.value.totalPages) loadPage(next)
            }
            DtcLookupEvent.PrevPage -> {
                val prev = _uiState.value.currentPage - 1
                if (prev >= 1) loadPage(prev)
            }
            is DtcLookupEvent.ShowDetail -> {
                _selectedEntity.value = event.entity
            }
            DtcLookupEvent.DismissDetail -> {
                _selectedEntity.value = null
            }
        }
    }

    private fun onSearch(query: String) {
        // Update the state immediately so the text field feels responsive
        _uiState.value = _uiState.value.copy(searchQuery = query)

        // Cancel any pending search
        searchJob?.cancel()

        // Debounce 300ms before executing
        searchJob = viewModelScope.launch {
            delay(300)
            loadPage(page = 1)  // always reset to page 1 on new search
        }
    }

    private fun loadPage(page: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val query = _uiState.value.searchQuery.trim()
            val limit = _uiState.value.pageSize
            val offset = (page - 1) * limit

            if (query.isEmpty()) {
                // Browse all
                val total = dtcDao.getTotalCount()
                val items = dtcDao.getPage(limit, offset)
                _uiState.value = _uiState.value.copy(
                    items = items,
                    currentPage = page,
                    totalPages = ((total + limit - 1) / limit).coerceAtLeast(0),
                    totalItems = total,
                    isLoading = false
                )
            } else {
                // Search
                val total = dtcDao.searchCount(query)
                val items = dtcDao.searchPage(query, limit, offset)
                _uiState.value = _uiState.value.copy(
                    items = items,
                    currentPage = page,
                    totalPages = ((total + limit - 1) / limit).coerceAtLeast(0),
                    totalItems = total,
                    isLoading = false
                )
            }
        }
    }

    init {
        loadPage(1)
    }
}
