package com.tired.app.data.remote.api

import com.tired.app.data.remote.model.BackendChatRequest
import com.tired.app.data.remote.model.BackendChatResponse
import com.tired.app.data.remote.model.ChatCompletionRequest
import com.tired.app.data.remote.model.ChatCompletionResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
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
}

