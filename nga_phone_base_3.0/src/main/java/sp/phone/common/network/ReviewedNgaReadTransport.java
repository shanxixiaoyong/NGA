package sp.phone.common.network;

import com.justwen.androidnga.base.network.request.FoundationAccessPolicy;
import com.justwen.androidnga.base.network.request.NgaRequestContext;
import com.justwen.androidnga.base.network.response.ClassifiedNgaResponse;
import com.justwen.androidnga.base.network.response.NgaResponseClassifier;
import com.justwen.androidnga.base.network.response.RawNgaResponse;
import com.justwen.androidnga.base.network.retrofit.RetrofitHelper;
import com.justwen.androidnga.base.network.retrofit.RetrofitService;
import com.justwent.androidnga.bu.session.AccountSessionSnapshot;

import java.io.IOException;
import java.util.Map;

import io.reactivex.Observable;
import retrofit2.Response;
import okhttp3.ResponseBody;
import sp.phone.common.UserManagerImpl;

/** Account-scoped transport for the small set of reviewed NGA reads. */
public final class ReviewedNgaReadTransport {

    private final RetrofitService service;
    private final UserManagerImpl userManager;
    private final NgaResponseClassifier responseClassifier;

    public ReviewedNgaReadTransport() {
        RetrofitHelper helper = RetrofitHelper.getInstance();
        this.service = helper.createRetrofit(
                        helper.createOkHttpClientBuilder(
                                FoundationAccessPolicy.enabledForReviewedReads()))
                .create(RetrofitService.class);
        this.userManager = UserManagerImpl.getInstance();
        this.responseClassifier = new NgaResponseClassifier();
    }

    public Observable<String> topicList(String url) {
        return get(FoundationAccessPolicy.READ_TOPIC_LIST, url, null);
    }

    public Observable<String> articleList(String url, Map<String, String> headers) {
        return get(FoundationAccessPolicy.READ_ARTICLE_LIST, url, headers);
    }

    private Observable<String> get(
            String operationId,
            String url,
            Map<String, String> headers
    ) {
        return Observable.defer(() -> {
            AccountSessionSnapshot session = userManager.captureActiveSession();
            NgaRequestContext context = requestContext(operationId, session);
            Observable<Response<ResponseBody>> request = headers == null
                    ? service.getUrlRaw(context, url)
                    : service.getUrlRaw(context, url, headers);
            return request.map(response -> payloadForCurrentSession(response, session));
        });
    }

    private NgaRequestContext requestContext(
            String operationId,
            AccountSessionSnapshot session
    ) {
        if (session.isAnonymous()) {
            return NgaRequestContext.anonymousRead(operationId);
        }
        return new NgaRequestContext(
                operationId,
                NgaRequestContext.Intent.READ,
                session.getAccountId(),
                session.getSessionGeneration(),
                session.getCookieHeader());
    }

    private String payloadForCurrentSession(
            Response<ResponseBody> response,
            AccountSessionSnapshot session
    ) throws IOException {
        ClassifiedNgaResponse classified = responseClassifier.classify(RawNgaResponse.from(response));
        if (classified.getType() != ClassifiedNgaResponse.Type.PAYLOAD) {
            throw new ReviewedReadRejectedException(classified.getType(), classified.getStatusCode());
        }
        if (!userManager.isSessionCurrent(session)) {
            throw new StaleSessionException();
        }
        return classified.getPayload();
    }

    private static final class ReviewedReadRejectedException extends IOException {

        ReviewedReadRejectedException(ClassifiedNgaResponse.Type type, int statusCode) {
            super("Reviewed NGA read rejected: " + type + " (HTTP " + statusCode + ")");
        }
    }

    private static final class StaleSessionException extends IOException {

        StaleSessionException() {
            super("Reviewed NGA read rejected: stale account session");
        }
    }
}
