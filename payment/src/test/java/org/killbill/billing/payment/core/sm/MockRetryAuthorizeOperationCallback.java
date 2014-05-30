/*
 * Copyright 2014 Groupon, Inc
 *
 * Groupon licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.payment.core.sm;

import org.killbill.automaton.OperationResult;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentStatus;
import org.killbill.billing.payment.core.DirectPaymentProcessor;
import org.killbill.billing.payment.dao.DirectPaymentModelDao;
import org.killbill.billing.payment.dao.DirectPaymentTransactionModelDao;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.retry.plugin.api.RetryPluginApi;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLocker;

public class MockRetryAuthorizeOperationCallback extends RetryAuthorizeOperationCallback {

    private final PaymentDao paymentDao;
    private final Clock clock;

    private Exception exception;
    private OperationResult result;

    public MockRetryAuthorizeOperationCallback(final GlobalLocker locker, final PluginDispatcher<OperationResult> paymentPluginDispatcher, final RetryableDirectPaymentStateContext directPaymentStateContext, final DirectPaymentProcessor directPaymentProcessor, final OSGIServiceRegistration<RetryPluginApi> retryPluginRegistry, final PaymentDao paymentDao, final Clock clock) {
        super(locker, paymentPluginDispatcher, directPaymentStateContext, directPaymentProcessor, retryPluginRegistry);
        this.paymentDao = paymentDao;
        this.clock = clock;
    }

    @Override
    protected OperationResult doPluginOperation() throws PaymentApiException {
        if (exception != null) {
            if (exception instanceof PaymentApiException) {
                throw (PaymentApiException) exception;
            } else {
                throw new RuntimeException(exception);
            }
        }
        if (result == OperationResult.SUCCESS) {
            final DirectPaymentModelDao payment = new DirectPaymentModelDao(clock.getUTCNow(),
                                                                            clock.getUTCNow(),
                                                                            directPaymentStateContext.account.getId(),
                                                                            directPaymentStateContext.paymentMethodId,
                                                                            directPaymentStateContext.directPaymentExternalKey);

            final DirectPaymentTransactionModelDao transaction = new DirectPaymentTransactionModelDao(clock.getUTCNow(),
                                                                                                      clock.getUTCNow(),
                                                                                                      directPaymentStateContext.directPaymentTransactionExternalKey,
                                                                                                      directPaymentStateContext.directPaymentId,
                                                                                                      directPaymentStateContext.transactionType,
                                                                                                      clock.getUTCNow(),
                                                                                                      PaymentStatus.SUCCESS,
                                                                                                      directPaymentStateContext.amount,
                                                                                                      directPaymentStateContext.currency,
                                                                                                      "",
                                                                                                      "");
            paymentDao.insertDirectPaymentWithFirstTransaction(payment, transaction, directPaymentStateContext.internalCallContext);
        }
        return result;
    }

    public MockRetryAuthorizeOperationCallback setException(final Exception exception) {
        this.exception = exception;
        return this;
    }

    public MockRetryAuthorizeOperationCallback setResult(final OperationResult result) {
        this.result = result;
        return this;
    }
}
