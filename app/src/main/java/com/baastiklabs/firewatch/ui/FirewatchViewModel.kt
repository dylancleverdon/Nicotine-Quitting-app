package com.baastiklabs.firewatch.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.baastiklabs.firewatch.FirewatchApp
import com.baastiklabs.firewatch.core.records.FirewatchData
import com.baastiklabs.firewatch.data.Repository
import com.baastiklabs.firewatch.update.UpdateState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FirewatchViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as FirewatchApp).graph

    val repository: Repository = graph.repository
    val updateState: UpdateState = graph.updateState

    val data: StateFlow<FirewatchData> = repository.data
    val loaded: StateFlow<Boolean> = repository.loaded
    val update: StateFlow<UpdateState.Snapshot> = updateState.flow

    /** Ticks so "time since" figures stay fresh. */
    val now: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(15_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    init {
        viewModelScope.launch { repository.ensureLoaded() }
    }
}
