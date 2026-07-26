package gov.anzong.androidnga.rxjava;

import gov.anzong.androidnga.common.util.LogUtils;
import io.reactivex.Observer;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;

public abstract class BaseSubscriber<T> implements Observer<T> {

    @Override
    public void onNext(@NonNull T t) {

    }

    @Override
    public void onComplete() {

    }

    @Override
    public void onError(@NonNull Throwable throwable) {
        LogUtils.e("request_failed type=" + throwable.getClass().getSimpleName());
    }

    @Override
    public void onSubscribe(@NonNull Disposable disposable) {

    }

}
