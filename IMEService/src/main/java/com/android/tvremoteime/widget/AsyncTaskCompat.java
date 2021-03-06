package com.android.tvremoteime.widget;

import android.os.AsyncTask;

/**
 * author : 徐亚彬
 * e-mail : xuyabin.521@163.com
 * date   : 2020/3/2816:55
 * desc   :
 * version: 1.0
 */
final class AsyncTaskCompat {

    @SafeVarargs
    static <Params, Progress, Result> void executeParallel(
            AsyncTask<Params, Progress, Result> task,
            Params... params) {
        if (task == null) {
            throw new IllegalArgumentException("task can not be null");
        }
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);
    }
    private AsyncTaskCompat() {}
}