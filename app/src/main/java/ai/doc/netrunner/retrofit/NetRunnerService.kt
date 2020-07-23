package ai.doc.netrunner.retrofit

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url

interface NetRunnerService {

    @Streaming @GET
    suspend fun downloadModel(@Url fileUrl: String): ResponseBody
}