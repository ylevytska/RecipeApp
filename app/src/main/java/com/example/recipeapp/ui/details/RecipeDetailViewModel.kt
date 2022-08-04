package com.example.recipeapp.ui.details

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recipeapp.data.models.Recipe
import com.example.recipeapp.data.repositories.RecipeRepository
import com.example.recipeapp.utils.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecipeDetailViewModel @Inject constructor(private val repository: RecipeRepository) :
    ViewModel() {

    private val _recipe = MutableLiveData<Recipe>()
    val recipe: LiveData<Recipe> = _recipe

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private val _progressBar = MutableLiveData<Boolean>()
    val progressBar: LiveData<Boolean> = _progressBar

    fun getRecipeById(id: Int) {
        _progressBar.postValue(true)
        viewModelScope.launch {
            when (val result = repository.getRecipeById(id)) {
                is Result.Success -> _recipe.postValue(result.data)
                is Result.Error -> _error.postValue(result.exception.toString())
            }
            _progressBar.postValue(false)
        }
    }
}