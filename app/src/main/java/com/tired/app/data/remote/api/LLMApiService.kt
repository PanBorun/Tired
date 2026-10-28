package com.tired.app.data.remote.api

import com.tired.app.data.remote.model.BackendChatRequest
import com.tired.app.data.remote.model.BackendChatResponse
import com.tired.app.data.remote.model.ChatCompletionRequest
import com.tired.app.data.remote.model.ChatCompletionResponse
import com.tired.app.data.remote.model.BackendTraitListResponse
import com.tired.app.data.remote.model.BackendTraitMutationRequest
import com.tired.app.data.remote.model.BackendTraitMutationResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Url

interface LLMApiService {
    @POST
    suspend fun chatCompletion(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse

    @POST
    suspend fun backendChat(
        @Url url: String,
        @Body request: BackendChatRequest
    ): BackendChatResponse

    @GET
    suspend fun getProfileTraits(
        @Url url: String
    ): BackendTraitListResponse

    @POST
    suspend fun addProfileTrait(
        @Url url: String,
        @Body request: BackendTraitMutationRequest
    ): BackendTraitMutationResponse

    @PUT
    suspend fun updateProfileTrait(
        @Url url: String,
        @Body request: BackendTraitMutationRequest
    ): BackendTraitMutationResponse

    @DELETE
    suspend fun deleteProfileTrait(
        @Url url: String
    ): BackendTraitMutationResponse

    @GET
    suspend fun getAntiFatiguePlan(
        @Url url: String
    ): com.tired.app.data.remote.model.BackendPlanResponse

    @DELETE
    suspend fun deleteAntiFatiguePlan(
        @Url url: String
    ): Map<String, String>
}

