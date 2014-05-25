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

import org.killbill.automaton.DefaultStateMachineConfig;
import org.killbill.automaton.OperationResult;
import org.killbill.automaton.StateMachineConfig;
import org.killbill.billing.account.api.Account;
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
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.PaymentConfig;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.bus.api.PersistentBus;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.xmlloader.XMLLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Resources;
import com.google.inject.name.Named;

import static org.killbill.billing.payment.glue.PaymentModule.PLUGIN_EXECUTOR_NAMED;

public class RetryableDirectPaymentProcessor extends ProcessorBase {

    private final TagInternalApi tagApi;
    private final RetryableDirectPaymentAutomatonRunner retryableDirectPaymentAutomatonRunner;
    private final InternalCallContextFactory internalCallContextFactory;
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
                                           final InternalCallContextFactory internalCallContextFactory,
                                           final Clock clock,
                                           final GlobalLocker locker,
                                           final PaymentConfig paymentConfig,
                                           @Named(PLUGIN_EXECUTOR_NAMED) final ExecutorService executor,
                                           final TagInternalApi tagApi,
                                           final DirectPaymentProcessor directPaymentProcessor) {
        super(pluginRegistry, accountUserApi, eventBus, paymentDao, nonEntityDao, tagUserApi, locker, executor, invoiceApi);

        this.internalCallContextFactory = internalCallContextFactory;
        this.tagApi = tagApi;
        this.directPaymentProcessor = directPaymentProcessor;
        final StateMachineConfig stateMachineConfig;
        try {
            stateMachineConfig = XMLLoader.getObjectFromString(Resources.getResource("RetryStates.xml").toExternalForm(), DefaultStateMachineConfig.class);
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }

        final long paymentPluginTimeoutSec = TimeUnit.SECONDS.convert(paymentConfig.getPaymentPluginTimeout().getPeriod(), paymentConfig.getPaymentPluginTimeout().getUnit());
        final PluginDispatcher<OperationResult> paymentPluginDispatcher = new PluginDispatcher<OperationResult>(paymentPluginTimeoutSec, executor);
        retryableDirectPaymentAutomatonRunner = new RetryableDirectPaymentAutomatonRunner(stateMachineConfig, paymentDao, locker, paymentPluginDispatcher, pluginRegistry, clock, this.tagApi, this.directPaymentProcessor);
    }

    public DirectPayment createAuthorization(final Account account, @Nullable final UUID directPaymentId, final BigDecimal amount, final Currency currency, final String directPaymentExternalKey, final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        final String directPaymentTransactionExternalKey = directPaymentExternalKey; // TODO API change
        final UUID paymentMethodId = account.getPaymentMethodId(); // TODO API change

        final UUID nonNullDirectPaymentId = retryableDirectPaymentAutomatonRunner.run(TransactionType.AUTHORIZE,
                                                                                      account,
                                                                                      paymentMethodId,
                                                                                      directPaymentId,
                                                                                      directPaymentExternalKey,
                                                                                      directPaymentTransactionExternalKey,
                                                                                      amount,
                                                                                      currency,
                                                                                      properties,
                                                                                      callContext,
                                                                                      internalCallContext);

        // TODO STEPH_RETRY extra get ?
        return directPaymentProcessor.getPayment(nonNullDirectPaymentId, true, properties, callContext, internalCallContext);
    }

}
