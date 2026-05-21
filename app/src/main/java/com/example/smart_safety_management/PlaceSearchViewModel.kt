package com.example.smart_safety_management

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class PlaceSearchViewModel(
    private val api: PlaceApi,
    private val restApiKey: String
) : ViewModel()
 {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _items = MutableStateFlow<List<PlaceSuggestion>>(emptyList())
    val items: StateFlow<List<PlaceSuggestion>> = _items

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    fun setQuery(q: String) { _query.value = q }

    init {
        viewModelScope.launch {
            _query
                .debounce(300)
                .distinctUntilChanged()
                .collectLatest { q ->
                    if (q.isBlank()) {
                        _items.value = emptyList()
                        return@collectLatest
                    }

                    _loading.value = true
                    try {
                        if (restApiKey.isBlank() || restApiKey.startsWith("SAMPLE")) {
                            Log.e("KakaoPlaceSearch", "Kakao REST API key is missing. Set kakao.restApiKey in local.properties.")
                            _items.value = emptyList()
                            return@collectLatest
                        }

                        val auth = "KakaoAK $restApiKey"
                        val res = api.keywordSearch(auth, q, 10)

                        val docs = if (res.isSuccessful) res.body()?.documents ?: emptyList() else emptyList()

                        _items.value = docs.map { d ->
                            PlaceSuggestion(
                                place_id = "${d.place_name}_${d.x}_${d.y}",
                                title = d.place_name ?: q,
                                subtitle = null,
                                address = d.address_name,
                                road_address = d.road_address_name,
                                zipcode = null, // 카카오 로컬은 기본 응답에 우편번호 없음
                                lat = d.y?.toDoubleOrNull(),
                                lon = d.x?.toDoubleOrNull()
                            )
                        }

                    } catch (_: Exception) {
                        _items.value = emptyList()
                    } finally {
                        _loading.value = false
                    }
                }
        }
    }
}

class PlaceSearchVmFactory(
    private val api: PlaceApi,
    private val restApiKey: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return PlaceSearchViewModel(api, restApiKey) as T
    }
}
