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

package org.killbill.billing.payment.core;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.killbill.automaton.OperationResult;
import org.killbill.automaton.State;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.DirectPayment;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.core.sm.RetryableDirectPaymentAutomatonRunner;
import org.killbill.billing.payment.dao.DirectPaymentModelDao;
import org.killbill.billing.payment.dao.DirectPaymentTransactionModelDao;
import org.killbill.billing.payment.dao.PaymentAttemptModelDao;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.retry.BaseRetryService.RetryServiceScheduler;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.config.PaymentConfig;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.bus.api.PersistentBus;
import org.killbill.commons.locker.GlobalLocker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.name.Named;

import static org.killbill.billing.payment.glue.PaymentModule.PLUGIN_EXECUTOR_NAMED;

public class RetryableDirectPaymentProcessor extends ProcessorBase {

    private final TagInternalApi tagApi;
    private final RetryableDirectPaymentAutomatonRunner retryableDirectPaymentAutomatonRunner;
    private final DirectPaymentProcessor directPaymentProcessor;

    private static final Logger log = LoggerFactory.getLogger(RetryableDirectPaymentProcessor.class);

    @Inject
    public RetryableDirectPaymentProcessor(final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry,
                                           final AccountInternalApi accountUserApi,
                                           final InvoiceInternalApi invoiceApi,
                                           final TagInternalApi tagUserApi,
                                           final PaymentDao paymentDao,
                                           final NonEntityDao nonEntityDao,
                                           final PersistentBus eventBus,
                                           final GlobalLocker locker,
                                           @Named(PLUGIN_EXECUTOR_NAMED) final ExecutorService executor,
                                           final TagInternalApi tagApi,
                                           final DirectPaymentProcessor directPaymentProcessor,
                                           final RetryableDirectPaymentAutomatonRunner retryableDirectPaymentAutomatonRunner) {
        super(pluginRegistry, accountUserApi, eventBus, paymentDao, nonEntityDao, tagUserApi, locker, executor, invoiceApi);

        this.tagApi = tagApi;
        this.directPaymentProcessor = directPaymentProcessor;
        this.retryableDirectPaymentAutomatonRunner = retryableDirectPaymentAutomatonRunner;
    }

    public DirectPayment createAuthorization(final Account account, final UUID paymentMethodId, @Nullable final UUID directPaymentId, final BigDecimal amount, final Currency currency, final String paymentExternalKey, final String transactionExternalKey, final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        final UUID nonNullDirectPaymentId = retryableDirectPaymentAutomatonRunner.run(TransactionType.AUTHORIZE,
                                                                                      account,
                                                                                      paymentMethodId,
                                                                                      directPaymentId,
                                                                                      paymentExternalKey,
                                                                                      transactionExternalKey,
                                                                                      amount,
                                                                                      currency,
                                                                                      properties,
                                                                                      callContext,
                                                                                      internalCallContext);
        return directPaymentProcessor.getPayment(nonNullDirectPaymentId, true, properties, callContext, internalCallContext);
    }

    public void retryPaymentTransaction(final String transactionExternalKey, final Iterable<PluginProperty> properties, final InternalCallContext internalCallContext) {
        try {

            final PaymentAttemptModelDao attempt = paymentDao.getPaymentAttemptByExternalKey(transactionExternalKey, internalCallContext);
            final DirectPaymentTransactionModelDao transaction = paymentDao.getDirectPaymentTransactionByExternalKey(transactionExternalKey, internalCallContext);
            final DirectPaymentModelDao payment = paymentDao.getDirectPayment(transaction.getDirectPaymentId(), internalCallContext);

            final Account account = accountInternalApi.getAccountById(payment.getAccountId(), internalCallContext);
            final UUID tenantId = nonEntityDao.retrieveIdFromObject(internalCallContext.getTenantRecordId(), ObjectType.TENANT);
            final CallContext callContext = internalCallContext.toCallContext(tenantId);

            final State state = retryableDirectPaymentAutomatonRunner.fetchState(attempt.getStateName());
            switch (transaction.getTransactionType()) {
                case AUTHORIZE:
                    final UUID nonNullDirectPaymentId = retryableDirectPaymentAutomatonRunner.run(state,
                                                                                                  TransactionType.AUTHORIZE,
                                                                                                  account,
                                                                                                  payment.getPaymentMethodId(),
                                                                                                  payment.getId(),
                                                                                                  payment.getExternalKey(),
                                                                                                  transactionExternalKey,
                                                                                                  transaction.getAmount(),
                                                                                                  transaction.getCurrency(),
                                                                                                  properties,
                                                                                                  callContext,
                                                                                                  internalCallContext);
                    directPaymentProcessor.getPayment(nonNullDirectPaymentId, true, properties, callContext, internalCallContext);
                    break;
                case CAPTURE:
                    break;
                case PURCHASE:
                    break;
                case CREDIT:
                    break;
                case VOID:
                    break;
                default:
                    throw new IllegalStateException("Unexpected transactionType " + transaction.getTransactionType());
            }

        } catch (AccountApiException e) {
            e.printStackTrace();
        } catch (PaymentApiException e) {
            e.printStackTrace();
        }

    }
}
