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

package org.killbill.billing.payment.api;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.joda.time.LocalDate;
import org.killbill.automaton.OperationResult;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.payment.PaymentTestSuiteNoDB;
import org.killbill.billing.payment.core.sm.MockRetryAuthorizeOperationCallback;
import org.killbill.billing.payment.core.sm.MockRetryableDirectPaymentAutomatonRunner;
import org.killbill.billing.payment.core.sm.RetryableDirectPaymentStateContext;
import org.killbill.billing.payment.dao.MockPaymentDao;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.killbill.commons.locker.memory.MemoryGlobalLocker;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class TestRetryableDirectPaymentApi extends PaymentTestSuiteNoDB {

    private Account account;
    private MockPaymentDao mockPaymentDao;
    private MockRetryableDirectPaymentAutomatonRunner mockRetryableDirectPaymentAutomatonRunner;
    private PluginDispatcher<OperationResult> paymentPluginDispatcher;


    private final UUID paymentMethodId = UUID.randomUUID();
    private final String directPaymentExternalKey = "foo";
    private final String directPaymentTransactionExternalKey = "foobar";
    private final BigDecimal amount = BigDecimal.ONE;
    private final Currency currency = Currency.EUR;
    private final ImmutableList<PluginProperty> emptyProperties = ImmutableList.<PluginProperty>of();

    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {
        super.beforeClass();
        this.mockPaymentDao = (MockPaymentDao) paymentDao;
        this.mockRetryableDirectPaymentAutomatonRunner = (MockRetryableDirectPaymentAutomatonRunner) retryableDirectPaymentAutomatonRunner;
        account = testHelper.createTestAccount("lolo@gmail.com", false);
    }

    @Test(groups = "slow")
    public void testCreatePaymentWithNoDefaultPaymentMethod() throws InvoiceApiException, EventBusException, PaymentApiException {

        final RetryableDirectPaymentStateContext directPaymentStateContext =
                new RetryableDirectPaymentStateContext(null,
                                                       directPaymentExternalKey,
                                                       directPaymentTransactionExternalKey,
                                                       TransactionType.AUTHORIZE,
                                                       account,
                                                       paymentMethodId,
                                                       amount,
                                                       currency,
                                                       emptyProperties,
                                                       internalCallContext,
                                                       callContext);

        MockRetryAuthorizeOperationCallback mockRetryAuthorizeOperationCallback =
                new MockRetryAuthorizeOperationCallback(new MemoryGlobalLocker(),
                                                        mockRetryableDirectPaymentAutomatonRunner.getPaymentPluginDispatcher(),
                                                        directPaymentStateContext,
                                                        null,
                                                        mockRetryableDirectPaymentAutomatonRunner.getRetryPluginRegistry(),
                                                        paymentDao,
                                                        clock);

        mockRetryableDirectPaymentAutomatonRunner
                .setOperationCallback(mockRetryAuthorizeOperationCallback)
                .setContext(directPaymentStateContext);

        final DirectPayment result = retryablePaymentApi.createAuthorization(account, paymentMethodId, null, amount, currency, directPaymentExternalKey,
                                                                             directPaymentTransactionExternalKey, emptyProperties, callContext);
    }
}