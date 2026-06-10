package net.jgpower.gichan_land.data.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object AppVisibilityState {
    private val _isForeground = MutableStateFlow(false)
    val isForeground: StateFlow<Boolean> = _isForeground

    fun setForeground(value: Boolean) {
        _isForeground.value = value
    }
}