package sp.phone.mvp.model;

import android.text.TextUtils;

import com.trello.rxlifecycle2.android.FragmentEvent;

import org.apache.commons.io.FileUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Map;

import gov.anzong.androidnga.base.util.ContextUtils;
import gov.anzong.androidnga.base.util.ThreadUtils;
import gov.anzong.androidnga.base.util.ToastUtils;
import gov.anzong.androidnga.http.OnHttpCallBack;
import io.reactivex.Observable;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.schedulers.Schedulers;
import sp.phone.http.bean.ThreadData;
import sp.phone.http.bean.ThreadRowInfo;
import com.justwen.androidnga.base.network.retrofit.RetrofitHelper;
import com.justwen.androidnga.base.network.retrofit.RetrofitService;
import sp.phone.mvp.contract.ArticleListContract;
import sp.phone.mvp.model.convert.ArticleConvertFactory;
import sp.phone.mvp.model.convert.ErrorConvertFactory;
import sp.phone.mvp.model.web.NgaNativeArticleRequestPolicy;
import sp.phone.mvp.model.web.NgaNativeArticleRequestPolicy.WireFormat;
import sp.phone.mvp.model.web.NgaWebArticleFallbackPolicy;
import sp.phone.mvp.model.web.NgaWebArticleFallbackSession;
import sp.phone.param.ArticleListParam;
import sp.phone.param.ContentSource;
import sp.phone.linuxdo.LinuxDoRepository;
import sp.phone.rxjava.BaseSubscriber;
import okhttp3.ResponseBody;

/**
 * 加载帖子内容
 * Created by Justwen on 2017/7/10.
 */

public class ArticleListModel extends BaseModel implements ArticleListContract.Model {

    private static final int MAX_JSON_RESPONSE_BYTES = 8 * 1024 * 1024;
    private RetrofitService mService;

    public ArticleListModel() {
        mService = (RetrofitService) RetrofitHelper.getInstance().getService(RetrofitService.class);
    }

    public String getUrl(ArticleListParam param) {
        return getUrl(param, param.page, WireFormat.LEGACY_GB18030);
    }

    private String getUrl(ArticleListParam param, int page, WireFormat format) {
        return NgaNativeArticleRequestPolicy.buildReadUrl(
                getAvailableDomain(), param, Math.max(1, page), format);
    }

    @Override
    public void loadPage(ArticleListParam param, final OnHttpCallBack<ThreadData> callBack) {
        loadPage(param, null, callBack);
    }

    @Override
    public void loadPage(ArticleListParam param, Map<String, String> header, OnHttpCallBack<ThreadData> callBack) {
        if (param.source == ContentSource.LINUX_DO) {
            try {
                LinuxDoRepository.getInstance().loadArticle(param.tid, Math.max(1, param.page), callBack);
            } catch (RuntimeException | LinkageError error) {
                callBack.onError("LINUX DO 初始化失败，请稍后重试");
            }
            return;
        }
        if (param.topLikedPage) {
            loadTopLikedPage(param, header, callBack);
            return;
        }
        requestNgaPageWithRetry(param, header, param.page)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(getLifecycleProvider().<ThreadData>bindUntilEvent(FragmentEvent.DETACH))
                .subscribe(new BaseSubscriber<ThreadData>() {

                    @Override
                    public void onNext(@NonNull ThreadData threadData) {
                        callBack.onSuccess(threadData);
                    }

                    @Override
                    public void onError(@NonNull Throwable throwable) {
                        callBack.onError(ErrorConvertFactory.getErrorMessage(throwable), throwable);
                    }
                });
    }

    private void loadTopLikedPage(
            ArticleListParam param,
            Map<String, String> header,
            OnHttpCallBack<ThreadData> callBack) {
        requestNgaPageWithRetry(param, header, 1)
                .flatMap(firstPage -> {
                    NgaTopLikedPageAssembler assembler = new NgaTopLikedPageAssembler();
                    assembler.add(firstPage);
                    java.util.List<Integer> pages =
                            NgaTopLikedRequestPlanner.additionalPages(firstPage);
                    if (pages.isEmpty()) {
                        return Observable.just(assembler.finish());
                    }
                    return Observable.fromIterable(pages)
                            .concatMap(page -> requestNgaPageWithRetry(
                                    param, header, page))
                            .doOnNext(assembler::add)
                            .ignoreElements()
                            .andThen(Observable.fromCallable(assembler::finish));
                })
                .subscribeOn(Schedulers.io())
                .compose(getLifecycleProvider().<ThreadData>bindUntilEvent(FragmentEvent.DETACH))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new BaseSubscriber<ThreadData>() {
                    @Override
                    public void onNext(@NonNull ThreadData threadData) {
                        callBack.onSuccess(threadData);
                    }

                    @Override
                    public void onError(@NonNull Throwable throwable) {
                        callBack.onError(ErrorConvertFactory.getErrorMessage(throwable), throwable);
                    }
                });
    }

    /**
     * Bounded recovery for a truncated/malformed native JSON response. NGA's
     * web endpoint can return the same page with {@code noBBCode}; the
     * WebView-side extractor turns that page into ordinary THREAD.PAGE-shaped
     * rows containing raw UBB, and this method deliberately sends those rows
     * through parseArticleInfo (never the rendered HTML parser).
     */
    @Override
    public void loadWebFallbackPage(
            ArticleListParam param, OnHttpCallBack<ThreadData> callBack) {
        if (param == null || param.source == ContentSource.LINUX_DO) {
            callBack.onError("网页恢复不可用", new WebFallbackException());
            return;
        }
        final String url;
        try {
            url = NgaWebArticleFallbackPolicy.buildReadUrl(getAvailableDomain(), param);
        } catch (IllegalArgumentException error) {
            callBack.onError("网页恢复不可用", new WebFallbackException());
            return;
        }
        final int requestedTid = param.tid;
        final int requestedPid = param.pid;
        Observable.<String>create(emitter -> {
                    NgaWebArticleFallbackSession.RequestHandle handle =
                            NgaWebArticleFallbackSession.getInstance().load(
                                    url, new NgaWebArticleFallbackSession.Callback() {
                                        @Override
                                        public void onSuccess(String snapshot) {
                                            if (emitter.isDisposed()) return;
                                            emitter.onNext(snapshot);
                                            emitter.onComplete();
                                        }

                                        @Override
                                        public void onFailure(
                                                NgaWebArticleFallbackSession.Failure failure) {
                                            if (!emitter.isDisposed()) {
                                                emitter.onError(new WebFallbackException());
                                            }
                                        }
                                    });
                    emitter.setCancellable(handle::cancel);
                })
                .compose(getLifecycleProvider().<String>bindUntilEvent(FragmentEvent.DETACH))
                .observeOn(Schedulers.computation())
                .map(snapshot -> {
                    ArticleConvertFactory.ParseOutcome outcome =
                            ArticleConvertFactory.parseArticleInfo(snapshot);
                    ThreadData data = outcome.getData();
                    if (!isExpectedWebSnapshot(data, requestedTid, requestedPid)) {
                        throw new WebFallbackException();
                    }
                    return data;
                })
                .observeOn(AndroidSchedulers.mainThread())
                .compose(getLifecycleProvider().<ThreadData>bindUntilEvent(FragmentEvent.DETACH))
                .subscribe(new BaseSubscriber<ThreadData>() {
                    @Override
                    public void onNext(@NonNull ThreadData threadData) {
                        callBack.onSuccess(threadData);
                    }

                    @Override
                    public void onError(@NonNull Throwable throwable) {
                        callBack.onError("网页恢复失败", throwable);
                    }
                });
    }

    private static boolean isExpectedWebSnapshot(
            ThreadData data, int requestedTid, int requestedPid) {
        if (data == null || data.getThreadInfo() == null
                || data.getRowList() == null || data.getRowList().isEmpty()) {
            return false;
        }
        if (requestedTid > 0 && data.getThreadInfo().getTid() != requestedTid) return false;
        if (requestedPid <= 0) return true;
        for (ThreadRowInfo row : data.getRowList()) {
            if (row != null && row.getPid() == requestedPid) return true;
        }
        return false;
    }

    private Observable<ThreadData> requestNgaPageWithRetry(
            ArticleListParam param, Map<String, String> header, int page) {
        return requestNgaPageOnce(param, header, page, WireFormat.LEGACY_GB18030)
                .onErrorResumeNext(error -> {
                    if (!isRetryableNgaReadFailure(error)) {
                        return Observable.error(error);
                    }
                    return requestNgaPageOnce(param, header, page, WireFormat.UTF8_ARRAYS);
                });
    }

    private Observable<ThreadData> requestNgaPageOnce(
            ArticleListParam param, Map<String, String> header, int page, WireFormat format) {
        return Observable.defer(() -> {
            String url = getUrl(param, page, format);
            Observable<ResponseBody> request = header == null || header.isEmpty()
                    ? mService.getRaw(url)
                    : mService.getRaw(url, header);
            return request.map(body -> parseNgaResponse(
                    readBody(body, format.charset()), param.tid, Math.max(1, page)));
        });
    }

    private static boolean isRetryableNgaReadFailure(Throwable error) {
        return error instanceof ArticleParseException
                || error instanceof ServerException
                || error instanceof IOException;
    }

    private static ThreadData parseNgaResponse(String response, int tid, int page)
            throws Exception {
        ArticleConvertFactory.ParseOutcome outcome =
                ArticleConvertFactory.parseArticleInfo(response);
        ThreadData data = outcome.getData();
        if (data != null) return data;
        String errorMsg = ErrorConvertFactory.getErrorMessage(response);
        if (errorMsg != null) throw new Exception(errorMsg);
        if (outcome.getDiagnostic() != null) {
            throw new ArticleParseException(outcome.getDiagnostic().toUserMessage(tid, page));
        }
        throw new ServerException("NGA后台抽风了，请稍后重试第 " + page + " 页");
    }

    private static String readBody(ResponseBody body, Charset charset) throws IOException {
        try (ResponseBody closeable = body;
             InputStream input = closeable.byteStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_JSON_RESPONSE_BYTES) {
                    throw new IOException("NGA JSON response is too large");
                }
                output.write(buffer, 0, count);
            }
            String response = new String(output.toByteArray(), charset);
            String trimmed = response.trim();
            if (trimmed.startsWith("<") || trimmed.startsWith("<!DOCTYPE")) {
                throw new IOException("NGA native endpoint returned HTML");
            }
            return response;
        }
    }

    @Override
    public void cachePage(ArticleListParam param, String rawData) {

        if (TextUtils.isEmpty(param.topicInfo)) {
            ToastUtils.error("缓存失败！");
            return;
        }
        ThreadUtils.postOnSubThread(() -> {
            try {
                String path = ContextUtils.getContext().getFilesDir().getAbsolutePath() + "/cache/" + param.tid;
                File describeFile = new File(path, param.tid + ".json");
                FileUtils.write(describeFile, param.topicInfo);
                File rawDataFile = new File(path, param.page + ".json");
                FileUtils.write(rawDataFile, rawData);
                ToastUtils.success("缓存成功！");
            } catch (IOException e) {
                ToastUtils.error("缓存失败！");
                e.printStackTrace();
            }
        });
    }

    @Override
    public void loadCachePage(ArticleListParam param, OnHttpCallBack<ThreadData> callBack) {
        Observable.create((ObservableOnSubscribe<ThreadData>) emitter -> {
            String cachePath = ContextUtils.getContext().getFilesDir().getAbsolutePath()
                    + "/cache/" + param.tid + "/" + param.page + ".json";
            File cacheFile = new File(cachePath);
            String rawData = FileUtils.readFileToString(cacheFile);
            ThreadData threadData = rawData.contains("\"__WEB_FALLBACK_HTML\":true")
                    ? ArticleConvertFactory.parseWebArticleInfo(rawData).getData()
                    : ArticleConvertFactory.getArticleInfo(rawData);
            if (threadData != null) {
                emitter.onNext(threadData);
            } else {
                emitter.onError(new Exception());
            }
            emitter.onComplete();
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(new BaseSubscriber<ThreadData>() {
                    @Override
                    public void onNext(ThreadData threadData) {
                        callBack.onSuccess(threadData);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        callBack.onError("读取缓存失败！");
                    }
                });
    }

    public static class ServerException extends Exception {

        public ServerException(String message) {
            super(message);
        }
    }

    public static class ArticleParseException extends Exception {

        public ArticleParseException(String message) {
            super(message);
        }
    }

    public static class WebFallbackException extends Exception {

        public WebFallbackException() {
            super("NGA 网页恢复失败");
        }
    }

}
