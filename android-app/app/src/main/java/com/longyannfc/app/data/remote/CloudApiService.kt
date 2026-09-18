package com.longyannfc.app.data.remote

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import okhttp3.ResponseBody
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url

data class CloudEnvelope(
    @SerializedName("code") val code: Int = 0,
    @SerializedName("message") val message: JsonElement? = null,
    @SerializedName("data") val data: JsonElement? = null
) {
    val ok: Boolean get() = code == 2000 && data != null && !data.isJsonNull
    val errorText: String
        get() = when {
            message == null || message.isJsonNull -> "网络异常"
            message.isJsonPrimitive -> message.asString
            message.isJsonObject && message.asJsonObject.has("errmsg") ->
                message.asJsonObject.get("errmsg").asString
            else -> message.toString()
        }
}

data class AmiiboInfo(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("image") val image: String = "",
    @SerializedName("url") val url: String = "",
    @SerializedName("bin_url") val binUrl: String = "",
    @SerializedName("gameID") val gameId: Int = 0
) {
    val downloadUrl: String get() = url.ifBlank { binUrl }
}

data class AmiiboListResponse(
    val success: Boolean,
    val data: List<AmiiboInfo>?
)

data class LoginResponse(
    val success: Boolean,
    val token: String?,
    val openid: String?,
    val username: String? = null,
    val session: String? = null,
    val message: String? = null
)

data class UserInfoResponse(
    val success: Boolean,
    val nickname: String?,
    val vip: Boolean,
    val vipExpire: String?
)

interface CloudApiService {
    @GET("amiibo/amiibolist.zip")
    suspend fun downloadAmiiboList(): ResponseBody

    @GET
    suspend fun downloadAmiiboBin(@Url url: String): ResponseBody

    @FormUrlEncoded
    @POST("cc/brute.php")
    suspend fun bruteForce(
        @Field("session") session: String,
        @Field("username") username: String,
        @Field("action") action: String = "brute",
        @Field("datas") datas: String
    ): CloudEnvelope

    @FormUrlEncoded
    @POST("cc/nested.php")
    suspend fun nested(
        @Field("session") session: String,
        @Field("username") username: String,
        @Field("action") action: String = "nested",
        @Field("uid") uid: String,
        @Field("distance") distance: String,
        @Field("datas") datas: String
    ): CloudEnvelope

    @FormUrlEncoded
    @POST("cc/ds.php")
    suspend fun darkside(
        @Field("session") session: String,
        @Field("username") username: String,
        @Field("action") action: String = "darkside",
        @Field("uid") uid: String,
        @Field("datas") datas: String
    ): CloudEnvelope

    @FormUrlEncoded
    @POST("com/det_fr.php")
    suspend fun detectFrame(
        @Field("session") session: String,
        @Field("username") username: String,
        @Field("action") action: String = "distance",
        @Field("devid") devid: String,
        @Field("datas") datas: String
    ): CloudEnvelope

    @FormUrlEncoded
    @POST("com/det.php")
    suspend fun detectDistance(
        @Field("session") session: String,
        @Field("username") username: String,
        @Field("action") action: String = "distance",
        @Field("datas") datas: String
    ): CloudEnvelope

    @FormUrlEncoded
    @POST("cc/det.php")
    suspend fun detect(
        @Field("session") session: String,
        @Field("username") username: String,
        @Field("action") action: String,
        @Field("datas") datas: String,
        @Field("devid") devid: String = ""
    ): CloudEnvelope

    @FormUrlEncoded
    @POST("S3/s34nts.php")
    suspend fun staticNestedS3(
        @Field("session") session: String,
        @Field("username") username: String,
        @Field("action") action: String,
        @Field("datas") datas: String
    ): CloudEnvelope

    @FormUrlEncoded
    @POST("S4/s31nt.php")
    suspend fun staticNestedS4(
        @Field("session") session: String,
        @Field("username") username: String,
        @Field("action") action: String = "staticnested_1nt",
        @Field("datas") datas: String
    ): CloudEnvelope

    @FormUrlEncoded
    @POST("wx/wxloginfr.php")
    suspend fun login(
        @Field("code") code: String,
        @Field("phoneCode") phoneCode: String
    ): CloudEnvelope

    @FormUrlEncoded
    @POST("wx/wxusermanage.php")
    suspend fun userManage(
        @Field("session") session: String,
        @Field("username") username: String,
        @Field("action") action: String,
        @Field("passwd") passwd: String? = null,
        @Field("serialnum") serialnum: String? = null
    ): CloudEnvelope

    @FormUrlEncoded
    @POST("wx/sign.php")
    suspend fun sign(
        @Field("session") session: String,
        @Field("username") username: String,
        @Field("action") action: String
    ): CloudEnvelope

    @FormUrlEncoded
    @POST("wx/historykey.php")
    suspend fun historyKey(
        @Field("session") session: String,
        @Field("username") username: String,
        @Field("action") action: String,
        @Field("hkeys") hkeys: String? = null
    ): CloudEnvelope

    @FormUrlEncoded
    @POST("upload/wxdumpmanage.php")
    suspend fun dumpManage(
        @Field("session") session: String,
        @Field("username") username: String,
        @Field("action") action: String,
        @Field("ID") id: String? = null,
        @Field("carduid") carduid: String? = null,
        @Field("cardname") cardname: String? = null,
        @Field("cardtype") cardtype: String? = null,
        @Field("download_address") downloadAddress: String? = null,
        @Field("cardfreq") cardfreq: String? = null,
        @Field("dumpfile") dumpfile: String? = null,
        @Field("shareid") shareid: String? = null
    ): CloudEnvelope

    @GET("app/app_dfu_package.json")
    suspend fun getDfuInfo(): ResponseBody
}
