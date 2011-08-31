/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.util;

import hudson.util.ThreadPoolExecutorWithCallback.Callback;
import hudson.util.ThreadPoolExecutorWithCallback.FutureWithCallback;
import junit.framework.TestCase;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * @author Kohsuke Kawaguchi
 */
public class ThreadPoolExecutorWithCallbackTest extends TestCase {
    private ExecutorService base;
    ThreadPoolExecutorWithCallback es;


    @Override
    public void setUp() throws Exception {
        super.setUp();
        base = Executors.newFixedThreadPool(1);
        es = new ThreadPoolExecutorWithCallback(base);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (base!=null)
            base.shutdown();
    }

    public void testCallbackInvocation() throws Exception {
        final OneShotEvent ev = new OneShotEvent(); // used to block the completion of the task until we add callback
        final OneShotEvent cmp = new OneShotEvent(); // used to block until the callback is invoked

        final FutureWithCallback<Object> f = (FutureWithCallback<Object>) es.submit(new Callable<Object>() {
            public Object call() throws Exception {
                ev.block();
                return EUREKA;
            }
        });
        f.addCallback(new Callback<Object>() {
            public void onCompleted(Future<Object> v) {
                assertSame(v, f);
                assertTrue(f.isDone());
                cmp.signal();
            }
        });
        ev.signal();
        assertEquals(EUREKA, f.get());
        cmp.block(10*1000);
    }

    /**
     * Adding callback after the fact
     */
    public void testCallbackAfterCompletion() throws Exception {
        final FutureWithCallback<Object> f = (FutureWithCallback<Object>) es.submit(new Callable<Object>() {
            public Object call() throws Exception {
                return EUREKA;
            }
        });
        assertEquals(EUREKA, f.get());

        final boolean[] called = new boolean[1];
        f.addCallback(new Callback<Object>() {
            public void onCompleted(Future<Object> v) {
                assertSame(v, f);
                called[0] = true;
            }
        });
        assertTrue(called[0]);
    }

    private static final Object EUREKA = new Object();
}
