package com.justwen.androidnga.base.network.retrofit;

import android.content.Context;
import android.content.SharedPreferences;

import com.justwen.androidnga.base.network.request.FoundationAccessPolicy;
import com.justwen.androidnga.base.network.request.NgaRequestInterceptors;
import com.justwen.androidnga.base.network.retrofit.converter.JsonStringConvertFactory;

import java.net.URLDecoder;

import gov.anzong.androidnga.base.util.ContextUtils;
import gov.anzong.androidnga.base.util.StringUtils;
import gov.anzong.androidnga.common.PreferenceKey;
import gov.anzong.androidnga.common.util.ForumUtils;
import gov.anzong.androidnga.common.util.NLog;
import gov.anzong.androidnga.common.util.NgaRequestPolicy;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;

/**
 * Created by Justwen on 2017/10/10.
 */

public class RetrofitHelper {

    private Retrofit mRetrofit;

    private static final String URL_NGA_BASE_CC = "https://bbs.ngacn.cc/";

    private String mBaseUrl;

    private final String mUserAgent = NgaRequestPolicy.DEFAULT_USER_AGENT;

    private RetrofitHelper() {
        Context context = ContextUtils.getContext();
        SharedPreferences sp = context.getSharedPreferences(PreferenceKey.PERFERENCE, Context.MODE_PRIVATE);
        mBaseUrl = ForumUtils.getAvailableDomain();
        mRetrofit = createRetrofit();

        sp.registerOnSharedPreferenceChangeListener((sp1, key) -> {
            if (key.equals(PreferenceKey.KEY_NGA_DOMAIN)) {
                mBaseUrl = ForumUtils.getAvailableDomain();
                mRetrofit = createRetrofit();
            }
        });

    }

    public String getUserAgent() {
        return mUserAgent;
    }

    public Retrofit createRetrofit() {
        return createRetrofit(mBaseUrl, null);
    }

    public Retrofit createRetrofit(String baseUrl) {
        return createRetrofit(baseUrl, null);
    }

    public Retrofit createRetrofit(OkHttpClient.Builder builder) {
        return createRetrofit(mBaseUrl, builder);
    }

    public Retrofit createRetrofit(String baseUrl, OkHttpClient.Builder builder) {
        return createRetrofit(baseUrl, builder, FoundationAccessPolicy.disabled());
    }

    public Retrofit createRetrofit(
            String baseUrl,
            OkHttpClient.Builder builder,
            FoundationAccessPolicy accessPolicy
    ) {
        if (builder == null) {
            builder = createOkHttpClientBuilder(accessPolicy);
        }
        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(JsonStringConvertFactory.create())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .client(builder.build())
                .build();
    }

    public OkHttpClient.Builder createOkHttpClientBuilder() {
        return createOkHttpClientBuilder(FoundationAccessPolicy.disabled());
    }

    public OkHttpClient.Builder createOkHttpClientBuilder(FoundationAccessPolicy accessPolicy) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.addInterceptor(NgaRequestInterceptors.foundationGate(accessPolicy));
        builder.addInterceptor(chain -> {
            okhttp3.Request request = chain.request();
            try {
                if (request.method().equalsIgnoreCase("post")) {
                    String body = StringUtils.requestBody2String(request.body());
                    body = URLDecoder.decode(body, "utf-8");
                    if (body.contains("charset=gbk") || body.contains("charset=GBK")) {
                        request = request.newBuilder().post(RequestBody.create(MediaType.parse("application/x-www-form-urlencoded;charset=GBK"), body)).build();
                    }
                }
            } catch (Exception e) {
                NLog.e("request_encoding_failed type=" + e.getClass().getSimpleName());
            }
            return chain.proceed(request);
        });
        builder.addNetworkInterceptor(NgaRequestInterceptors.credentialBoundary(mUserAgent));
        return builder;
    }

    public static RetrofitHelper getInstance() {
        return SingleTonHolder.sInstance;
    }

    public Object getService(Class<?> service) {
        return mRetrofit.create(service);
    }

    public RetrofitService getService() {
        return mRetrofit.create(RetrofitService.class);
    }

    public RetrofitServiceKt getServiceKt() {
        return mRetrofit.create(RetrofitServiceKt.class);
    }

    public static RetrofitService getAuthCodeService() {
        return new Retrofit.Builder()
                .baseUrl(URL_NGA_BASE_CC)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .client(new OkHttpClient.Builder()
                        .addInterceptor(NgaRequestInterceptors.foundationGate(
                                FoundationAccessPolicy.disabled()))
                        .addNetworkInterceptor(NgaRequestInterceptors.credentialBoundary(
                                NgaRequestPolicy.DEFAULT_USER_AGENT))
                        .build())
                .build()
                .create(RetrofitService.class);
    }

    public static RetrofitService getDefault() {
        return new Retrofit.Builder()
                .baseUrl(URL_NGA_BASE_CC)
                .addConverterFactory(JsonStringConvertFactory.create())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .client(new OkHttpClient.Builder()
                        .addInterceptor(NgaRequestInterceptors.foundationGate(
                                FoundationAccessPolicy.disabled()))
                        .addNetworkInterceptor(NgaRequestInterceptors.credentialBoundary(
                                NgaRequestPolicy.DEFAULT_USER_AGENT))
                        .build())
                .build()
                .create(RetrofitService.class);
    }

    private static class SingleTonHolder {

        static final RetrofitHelper sInstance = new RetrofitHelper();
    }
}
