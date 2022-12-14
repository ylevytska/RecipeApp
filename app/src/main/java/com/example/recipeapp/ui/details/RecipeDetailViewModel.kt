package com.example.recipeapp.ui.details

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recipeapp.data.models.ExtendedIngredient
import com.example.recipeapp.data.models.CalculatedIngredient
import com.example.recipeapp.data.models.Recipe
import com.example.recipeapp.data.repositories.RecipeRepository
import com.example.recipeapp.utils.Constants
import com.example.recipeapp.utils.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.ArrayList
import javax.inject.Inject

@HiltViewModel
class RecipeDetailViewModel @Inject constructor(
    private val repository: RecipeRepository,
    private val firebaseAuth: FirebaseAuth,
    private val collectionReference: CollectionReference
) :
    ViewModel() {

    private val _recipe = MutableLiveData<Recipe>()
    val recipe: LiveData<Recipe> = _recipe

    private val _isFavourite = MutableLiveData(false)
    val isFavourite: LiveData<Boolean> = _isFavourite

    private val _calculatedIngredient = MutableLiveData<CalculatedIngredient>()
    val calculatedIngredient: LiveData<CalculatedIngredient> = _calculatedIngredient

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private var defaultServings = 0
    private var defaultCalories = 0
    private var defaultIngredients = emptyList<ExtendedIngredient>()
    private var previousUserInput = 0

    fun setFavorite(recipeId: Int) {
        viewModelScope.launch {
            val documentReference = collectionReference.document(firebaseAuth.uid ?: "")
            documentReference.collection(Constants.FAVORITE_COLLECTION)
                .whereEqualTo(Constants.RECIPE_ID_FIELD, recipeId)
                .limit(1)
                .get()
                .addOnSuccessListener { recipeDocs ->
                    if (recipeDocs.size() == 0) {
                        val recipeMap = hashMapOf<String, Int>()
                        recipeMap[Constants.RECIPE_ID_FIELD] = recipeId
                        documentReference.collection(Constants.FAVORITE_COLLECTION)
                            .add(recipeMap)
                            .addOnSuccessListener {
                                _isFavourite.postValue(true)
                            }
                            .addOnFailureListener {
                                _error.postValue(it.message)
                            }
                    } else {
                        documentReference.collection(Constants.FAVORITE_COLLECTION)
                            .document(recipeDocs.documents[0].id).delete()
                        _isFavourite.postValue(false)
                    }
                }
                .addOnFailureListener { exception ->
                    _error.postValue(exception.message)
                }
        }
    }


    fun getRecipeById(recipeId: Long) {
        viewModelScope.launch {
            when (val result = repository.getRecipeById(recipeId)) {
                is Result.Success -> {

                    // Convert HTML tags in summary text
                    val regex = "(\\d+) calories".toRegex()
                    val match = regex.find(result.data.summary)
                    val caloriesAmount = match?.value?.split(" ")
                    result.data.calories = caloriesAmount?.get(0)?.toInt() ?: 0

                    _calculatedIngredient.postValue(
                        CalculatedIngredient(
                            result.data.calories,
                            result.data.extendedIngredients
                        )
                    )

                    val documentReference = collectionReference.document(firebaseAuth.uid ?: "")
                    documentReference.collection(Constants.FAVORITE_COLLECTION)
                        .whereEqualTo(Constants.RECIPE_ID_FIELD, recipeId)
                        .limit(1)
                        .get()
                        .addOnSuccessListener { recipeDocs ->
                            if (recipeDocs.size() != 0) {
                                _isFavourite.postValue(true)
                            } else {
                                _isFavourite.postValue(false)
                            }
                        }
                        .addOnFailureListener { exception ->
                            _error.postValue(exception.message)
                        }

                    _recipe.postValue(result.data)

                    // Save values for future user calculation
                    defaultServings = result.data.servings
                    defaultIngredients = result.data.extendedIngredients
                    defaultCalories = caloriesAmount?.get(0)?.toInt() ?: 0
                }
                is Result.Error -> _error.postValue(result.exception.toString())
            }
        }
    }

    fun calculateNewCaloriesAndIngredientsAmount(userInputServings: Int) {
        if (userInputServings != previousUserInput) {
            previousUserInput = userInputServings
            viewModelScope.launch(Dispatchers.Default) {
                val newCalories = (defaultCalories / defaultServings) * userInputServings
                val ingredientWithNewAmount = ArrayList<ExtendedIngredient>()

                for (i in defaultIngredients.indices) {
                    val newExtendedIngredient = ExtendedIngredient(
                        aisle = defaultIngredients[i].aisle,
                        amount = (defaultIngredients[i].amount / defaultServings.toDouble()) * userInputServings,
                        consistency = defaultIngredients[i].consistency,
                        id = defaultIngredients[i].id,
                        image = defaultIngredients[i].image,
                        measures = defaultIngredients[i].measures,
                        meta = defaultIngredients[i].meta,
                        name = defaultIngredients[i].name,
                        nameClean = defaultIngredients[i].nameClean,
                        original = defaultIngredients[i].original,
                        originalName = defaultIngredients[i].originalName,
                        unit = defaultIngredients[i].unit
                    )
                    ingredientWithNewAmount.add(newExtendedIngredient)
                }

                _calculatedIngredient.postValue(
                    CalculatedIngredient(
                        newCalories,
                        ingredientWithNewAmount
                    )
                )
            }
        }
    }
}