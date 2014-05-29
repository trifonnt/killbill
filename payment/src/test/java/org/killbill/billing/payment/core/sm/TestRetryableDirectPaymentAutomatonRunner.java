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

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import javax.inject.Named;

import org.killbill.automaton.OperationResult;
import org.killbill.automaton.StateMachineConfig;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.PaymentTestSuiteNoDB;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.core.DirectPaymentProcessor;
import org.killbill.billing.payment.dao.MockPaymentDao;
import org.killbill.billing.payment.dao.PaymentAttemptModelDao;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.payment.glue.PaymentModule;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.retry.BaseRetryService.RetryServiceScheduler;
import org.killbill.billing.retry.plugin.api.RetryPluginApi;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.memory.MemoryGlobalLocker;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import static org.killbill.billing.payment.glue.PaymentModule.PLUGIN_EXECUTOR_NAMED;
import static org.killbill.billing.payment.glue.PaymentModule.RETRYABLE_NAMED;
import static org.testng.Assert.assertEquals;

public class TestRetryableDirectPaymentAutomatonRunner extends PaymentTestSuiteNoDB {

    @Inject
    @Named(PaymentModule.STATE_MACHINE_PAYMENT)
    private StateMachineConfig stateMachineConfig;
    @Inject
    @Named(PaymentModule.STATE_MACHINE_RETRY)
    private StateMachineConfig retryStateMachineConfig;
    @Inject
    private PaymentDao paymentDao;
    @Inject
    private GlobalLocker locker;
    @Inject
    private OSGIServiceRegistration<PaymentPluginApi> pluginRegistry;
    @Inject
    private OSGIServiceRegistration<RetryPluginApi> retryPluginRegistry;
    @Inject
    private TagInternalApi tagApi;
    @Inject
    private DirectPaymentProcessor directPaymentProcessor;
    @Inject
    @Named(RETRYABLE_NAMED)
    private RetryServiceScheduler retryServiceScheduler;
    @Inject
    @Named(PLUGIN_EXECUTOR_NAMED)
    private ExecutorService executor;

    private Account account;

    private final UUID paymentMethodId = UUID.randomUUID();
    private final String directPaymentExternalKey = "foo";
    private final String directPaymentTransactionExternalKey = "foobar";
    private final BigDecimal amount = BigDecimal.ONE;
    private final Currency currency = Currency.EUR;
    private final ImmutableList<PluginProperty> emptyProperties = ImmutableList.<PluginProperty>of();

    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {
        super.beforeClass();
        account = testHelper.createTestAccount("lolo@gmail.com", false);
    }

    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        ((MockPaymentDao) paymentDao).reset();
    }


    @Test(groups = "fast")
    public void testCreatePaymentWithNoDefaultPaymentMethod() throws InvoiceApiException, EventBusException, PaymentApiException {

        MockRetryableDirectPaymentAutomatonRunner runner = new MockRetryableDirectPaymentAutomatonRunner(
                stateMachineConfig,
                retryStateMachineConfig,
                paymentDao,
                locker,
                pluginRegistry,
                retryPluginRegistry,
                clock,
                tagApi,
                directPaymentProcessor,
                retryServiceScheduler,
                paymentConfig,
                executor);

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
                                                        runner.getPaymentPluginDispatcher(),
                                                        directPaymentStateContext,
                                                        null,
                                                        runner.getRetryPluginRegistry(),
                                                        paymentDao,
                                                        clock);

        mockRetryAuthorizeOperationCallback
                .setResult(OperationResult.SUCCESS)
                .setException(null);

        runner.setOperationCallback(mockRetryAuthorizeOperationCallback)
              .setContext(directPaymentStateContext);

        runner.run(TransactionType.AUTHORIZE,
                   account,
                   paymentMethodId,
                   null,
                   directPaymentExternalKey,
                   directPaymentTransactionExternalKey,
                   amount,
                   currency,
                   emptyProperties,
                   callContext,
                   internalCallContext);

        final PaymentAttemptModelDao pa = ((MockPaymentDao) paymentDao).getPaymentAttemptByExternalKey(directPaymentTransactionExternalKey, internalCallContext);
        assertEquals(pa.getTransactionExternalKey(), directPaymentTransactionExternalKey);
        assertEquals(pa.getStateName(), "SUCCESS");
        assertEquals(pa.getOperationName(), "AUTHORIZE");
    }
}