/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.billing.payment.dispatcher;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.payment.PaymentTestSuiteNoDB;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.dispatcher.PluginDispatcher.PluginDispatcherReturnType;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestPluginDispatcher extends PaymentTestSuiteNoDB {

    private final PluginDispatcher<Void> voidPluginDispatcher = new PluginDispatcher<Void>(10, Executors.newSingleThreadExecutor());

    @Test(groups = "fast")
    public void testDispatchWithTimeout() throws TimeoutException, PaymentApiException {
        boolean gotIt = false;
        try {
            voidPluginDispatcher.dispatchWithTimeout(new Callable<PluginDispatcherReturnType<Void>>() {
                @Override
                public PluginDispatcherReturnType<Void> call() throws Exception {
                    Thread.sleep(1000);
                    return null;
                }
            }, 100, TimeUnit.MILLISECONDS);
            Assert.fail("Failed : should have had Timeout exception");
        } catch (final TimeoutException e) {
            gotIt = true;
        } catch (InterruptedException e) {
            Assert.fail("Failed : should have had Timeout exception");
        } catch (ExecutionException e) {
            Assert.fail("Failed : should have had Timeout exception");
        }
        Assert.assertTrue(gotIt);
    }

    @Test(groups = "fast")
    public void testDispatchWithPaymentApiException() throws TimeoutException, PaymentApiException {
        boolean gotIt = false;
        try {
            voidPluginDispatcher.dispatchWithTimeout(new Callable<PluginDispatcherReturnType<Void>>() {
                @Override
                public PluginDispatcherReturnType<Void> call() throws Exception {
                    throw new PaymentApiException(ErrorCode.PAYMENT_ADD_PAYMENT_METHOD, "foo", "foo");
                }
            }, 100, TimeUnit.MILLISECONDS);
            Assert.fail("Failed : should have had Timeout exception");
        } catch (final TimeoutException e) {
            Assert.fail("Failed : should have had PaymentApiException exception");
        } catch (InterruptedException e) {
            Assert.fail("Failed : should have had PaymentApiException exception");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof PaymentApiException) {
                gotIt = true;
            } else {
                Assert.fail("Failed : should have had PaymentApiException exception");
            }
        }
        Assert.assertTrue(gotIt);
    }

    @Test(groups = "fast")
    public void testDispatchWithRuntimeException() throws TimeoutException, PaymentApiException {
        boolean gotIt = false;
        try {
            voidPluginDispatcher.dispatchWithTimeout(new Callable<PluginDispatcherReturnType<Void>>() {
                @Override
                public PluginDispatcherReturnType<Void> call() throws Exception {
                    throw new RuntimeException("whatever");
                }
            }, 100, TimeUnit.MILLISECONDS);
            Assert.fail("Failed : should have had Timeout exception");
        } catch (final TimeoutException e) {
            Assert.fail("Failed : should have had RuntimeException exception");
        } catch (final RuntimeException e) {
            Assert.fail("Failed : should have had RuntimeException (wrapped in an ExecutionException)");
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException) {
                gotIt = true;
            } else {
                Assert.fail("Failed : should have had RuntimeException exception");
            }
        }
        Assert.assertTrue(gotIt);
    }
}
