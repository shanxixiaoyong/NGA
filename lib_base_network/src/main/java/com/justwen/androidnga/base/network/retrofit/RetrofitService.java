package com.justwen.androidnga.base.network.retrofit;

import java.util.Map;

import com.justwen.androidnga.base.network.request.NgaRequestContext;

import gov.anzong.androidnga.common.base.JavaBean;
import io.reactivex.Observable;
import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.http.Body;
import retrofit2.http.FieldMap;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.HeaderMap;
import retrofit2.http.POST;
import retrofit2.http.QueryMap;
import retrofit2.http.Tag;
import retrofit2.http.Url;

/**
 * Created by Justwen on 2017/10/10.
 */

public interface RetrofitService {

    @GET("nuke.php")
    Observable<Response<ResponseBody>> getRaw(
            @Tag NgaRequestContext context,
            @QueryMap Map<String, String> map);

    @GET("forum.php")
    Observable<Response<ResponseBody>> getByForumRaw(
            @Tag NgaRequestContext context,
            @QueryMap Map<String, String> map);

    @GET
    Observable<Response<ResponseBody>> getUrlRaw(
            @Tag NgaRequestContext context,
            @Url String url);

    @GET
    Observable<Response<ResponseBody>> getUrlRaw(
            @Tag NgaRequestContext context,
            @Url String url,
            @HeaderMap Map<String, String> headers);

    @POST
    Observable<Response<ResponseBody>> postUrlRaw(
            @Tag NgaRequestContext context,
            @Url String url);

    @FormUrlEncoded
    @POST("nuke.php")
    Observable<Response<ResponseBody>> postRaw(
            @Tag NgaRequestContext context,
            @FieldMap Map<String, String> map);

    @FormUrlEncoded
    @POST("nuke.php")
    Observable<Response<ResponseBody>> postRaw(
            @Tag NgaRequestContext context,
            @QueryMap Map<String, String> queryMap,
            @FieldMap Map<String, String> fieldMap);

    @POST
    Observable<Response<ResponseBody>> uploadFileRaw(
            @Tag NgaRequestContext context,
            @Url String url,
            @Body MultipartBody body);

    /** Legacy migration-only API. The transport rejects it because it has no request context. */
    @GET("nuke.php")
    Observable<String> get(@QueryMap Map<String, String> map);

    @GET("forum.php")
    Observable<String> getByForum(@QueryMap Map<String, String> map);

    @GET
    Observable<String> get(@Url String url);

    @POST
    Observable<String> post(@Url String url);

    @FormUrlEncoded
    @POST("nuke.php")
    Observable<String> post(@FieldMap Map<String, String> map);

    @FormUrlEncoded
    @POST("nuke.php")
    Observable<String> post(@QueryMap Map<String, String> queryMap, @FieldMap Map<String, String> fieldMap);

    @FormUrlEncoded
    @POST("nuke.php")
    Observable<String> login(@FieldMap Map<String, String> map);

    @POST
    Observable<ResponseBody> uploadFile(@Url String url, @Body MultipartBody body);

    @GET
    Observable<String> get(@Url String url, @HeaderMap Map<String,String> map);

}
