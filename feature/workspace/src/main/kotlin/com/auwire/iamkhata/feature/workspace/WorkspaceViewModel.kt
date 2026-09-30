package com.auwire.iamkhata.feature.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.auwire.iamkhata.core.data.DatasetRepository
import com.auwire.iamkhata.core.model.CleaningOperation
import com.auwire.iamkhata.core.model.ColumnRole
import com.auwire.iamkhata.core.model.ColumnType
import com.auwire.iamkhata.core.model.DatasetColumn
import com.auwire.iamkhata.core.model.FilterSpec
import com.auwire.iamkhata.core.model.PivotResult
import com.auwire.iamkhata.core.model.PivotSpec
import com.auwire.iamkhata.core.model.SortDirection
import com.auwire.iamkhata.core.model.SortSpec
import com.auwire.iamkhata.core.model.TablePage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val DEFAULT_PAGE_SIZE = 100

/** Immutable state rendered by the workspace screen. */
data class WorkspaceUiState(
    val datasetId: Long = 0,
    val columns: List<DatasetColumn> = emptyList(),
    val page: TablePage = TablePage(emptyList(), 0, 0, DEFAULT_PAGE_SIZE),
    val searchText: String = "",
    val sort: SortSpec? = null,
    val busy: Boolean = true,
    val message: String? = null,
    val pivot: PivotResult? = null,
)

/**
 * Coordinates UI intents with the repository.
 *
 * No DAO or SQL object is exposed to Compose, keeping presentation independent
 * of persistence and analytics implementation details.
 */
class WorkspaceViewModel(
    private val repository: DatasetRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(WorkspaceUiState())
    val state: StateFlow<WorkspaceUiState> = _state.asStateFlow()

    private var columnJob: Job? = null

    init {
        viewModelScope.launch {
            runCatching { repository.ensureDefaultLedger() }
                .onSuccess(::bindDataset)
                .onFailure(::fail)
        }
    }

    fun setSearchText(value: String) {
        _state.update { it.copy(searchText = value) }
    }

    fun applySearch() = refresh(offset = 0)

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    fun toggleSort(column: DatasetColumn) {
        val current = _state.value.sort
        val next = when {
            current?.columnId != column.id -> SortSpec(column.id, SortDirection.ASC)
            current.direction == SortDirection.ASC -> SortSpec(column.id, SortDirection.DESC)
            else -> null
        }
        _state.update { it.copy(sort = next) }
        refresh(offset = 0)
    }

    fun nextPage() {
        val state = _state.value
        val next = state.page.offset + state.page.limit
        if (next < state.page.totalRows) refresh(next)
    }

    fun previousPage() {
        val state = _state.value
        refresh((state.page.offset - state.page.limit).coerceAtLeast(0))
    }

    fun addColumn(name: String, type: ColumnType) = launchMutation {
        repository.addColumn(
            datasetId = currentDataset(),
            displayName = name,
            type = type,
            role = ColumnRole.NONE,
        )
        "Column added."
    }

    fun addRow(values: Map<Long, String>) = launchMutation {
        repository.appendRow(currentDataset(), values)
        "Row added."
    }

    fun cleanColumn(columnId: Long, operation: CleaningOperation) = launchMutation {
        repository.cleanColumn(currentDataset(), columnId, operation).description
    }

    fun runPivot(spec: PivotSpec) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            runCatching { repository.pivot(currentDataset(), spec) }
                .onSuccess { result -> _state.update { it.copy(busy = false, pivot = result) } }
                .onFailure(::fail)
        }
    }

    fun dismissPivot() {
        _state.update { it.copy(pivot = null) }
    }

    private fun bindDataset(datasetId: Long) {
        _state.update { it.copy(datasetId = datasetId, busy = true) }
        columnJob?.cancel()
        columnJob = viewModelScope.launch {
            repository.observeColumns(datasetId).collectLatest { columns ->
                _state.update { it.copy(columns = columns) }
                loadPage(0)
            }
        }
    }

    private fun refresh(offset: Int = _state.value.page.offset) {
        viewModelScope.launch { loadPage(offset) }
    }

    private suspend fun loadPage(offset: Int) {
        val state = _state.value
        if (state.datasetId == 0L) return

        _state.update { it.copy(busy = true) }
        runCatching {
            repository.loadPage(
                datasetId = state.datasetId,
                limit = DEFAULT_PAGE_SIZE,
                offset = offset,
                sort = state.sort,
                filter = state.searchText.trim()
                    .takeIf(String::isNotEmpty)
                    ?.let(::FilterSpec),
            )
        }.onSuccess { page ->
            _state.update { it.copy(page = page, busy = false) }
        }.onFailure(::fail)
    }

    private fun launchMutation(block: suspend () -> String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            runCatching { block() }
                .onSuccess { message ->
                    _state.update { it.copy(message = message) }
                    loadPage(0)
                }
                .onFailure(::fail)
        }
    }

    private fun currentDataset(): Long =
        _state.value.datasetId.takeIf { it != 0L }
            ?: error("Dataset has not finished loading.")

    private fun fail(error: Throwable) {
        _state.update {
            it.copy(
                busy = false,
                message = error.message ?: "The operation could not be completed.",
            )
        }
    }
}

/** Small explicit factory keeps dependency injection framework-independent. */
class WorkspaceViewModelFactory(
    private val repository: DatasetRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(WorkspaceViewModel::class.java))
        return WorkspaceViewModel(repository) as T
    }
}
