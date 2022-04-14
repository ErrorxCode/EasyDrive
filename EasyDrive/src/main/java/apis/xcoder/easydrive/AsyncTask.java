package apis.xcoder.easydrive;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nonnull;

/**
 * AsyncTask is a convenience class for running asynchronous tasks.
 * This is java port of google play services Tasks api. This can be used in both android
 * and non-android projects. However, in android, you manually have to wrap the interface/callback
 * method to run in ui thread as by default, the callback also runs in background thread.
 * @param <T>
 */
public class AsyncTask<T> {
    public Exception exception;
    public T result;
    public boolean isSuccessful;
    private OnCompleteCallback<T> completeCallback = call -> {};
    private OnErrorCallback errorCallback = e -> {};
    private OnSuccessCallback<T> successCallback = result -> {};

    /**
     * Executes the task asynchronously. This method returns immediately. It takes a callable which
     * if returns the value, the task is considered successful. If the callable throws an exception,
     * the task is considered failed.
     * @param callable the task to execute
     * @param <T> the type of the result
     * @return the call that can be used to get the result in future
     */
    public static <T> AsyncTask<T> callAsync(Callable<T> callable) {
        AsyncTask<T> call = new AsyncTask<>();
        new Thread(() -> {
            try {
                Thread.sleep(1);
                T result = callable.call();
                call.result = result;
                call.isSuccessful = true;
                call.successCallback.onSuccess(result);
            } catch (Exception e) {
                call.exception = e;
                call.errorCallback.onError(e);
            }
            call.completeCallback.onComplete(call);
        }).start();
        return call;
    }

    /**
     * Makes the call synchronous and returns the result of the task. This method will block until the task is complete.
     * if the result is not returned in time, it will throw an exception.
     * @param call the call to wait for
     * @param timeoutSeconds the maximum time (in seconds) to wait for the task to complete
     * @param <T> the type of the result
     * @return the result of the task
     * @throws Exception if the task failed or the timeout was reached.
     */
    public static <T> T await(@Nonnull AsyncTask<T> call, int timeoutSeconds) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<Exception> exception = new AtomicReference<>();
        call.setOnCompleteCallback((task) -> {
            if (task.isSuccessful) {
                value.set(task.result);
            } else
                exception.set(task.exception);

            latch.countDown();
        });
        boolean completed = latch.await(timeoutSeconds, TimeUnit.SECONDS);
        if (exception.get() != null)
            throw exception.get();
        else if (!completed)
            throw new Exception("Operation timeout");
        else
            return value.get();
    }

    public AsyncTask<T> setOnSuccessCallback(@Nonnull OnSuccessCallback<T> onSuccessCallback) {
        this.successCallback = onSuccessCallback;
        return this;
    }

    public AsyncTask<T> setOnErrorCallback(@Nonnull OnErrorCallback onErrorCallback) {
        this.errorCallback = onErrorCallback;
        return this;
    }

    public void setOnCompleteCallback(@Nonnull OnCompleteCallback<T> onCompleteCallback) {
        this.completeCallback = onCompleteCallback;
    }


    public interface OnCompleteCallback<T> {
        void onComplete(AsyncTask<T> call);
    }

    public interface OnSuccessCallback<T> {
        void onSuccess(T result);
    }

    public interface OnErrorCallback {
        void onError(Exception e);
    }
}
