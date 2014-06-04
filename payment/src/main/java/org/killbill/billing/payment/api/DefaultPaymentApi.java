/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.osgi.api.OSGIServiceDescriptor;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.core.DirectPaymentProcessor;
import org.killbill.billing.payment.core.PaymentMethodProcessor;
import org.killbill.billing.payment.core.PaymentProcessor;
import org.killbill.billing.payment.core.RefundProcessor;
import org.killbill.billing.payment.core.sm.RetryableDirectPaymentAutomatonRunner;
import org.killbill.billing.payment.dao.DirectPaymentModelDao;
import org.killbill.billing.payment.dao.DirectPaymentTransactionModelDao;
import org.killbill.billing.payment.dao.PaymentAttemptModelDao;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.retry.DefaultRetryPluginResult;
import org.killbill.billing.retry.plugin.api.RetryPluginApi;
import org.killbill.billing.retry.plugin.api.RetryPluginApiException;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.config.PaymentConfig;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.clock.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

public class DefaultPaymentApi implements PaymentApi {

    private final static String INVOICE_PLUGIN_NAME = "__INVOICE_RETRY_PLUGIN__";

    private final PaymentMethodProcessor methodProcessor;
    private final PaymentProcessor paymentProcessor;
    private final RefundProcessor refundProcessor;
    private final InternalCallContextFactory internalCallContextFactory;
    private final Clock clock;
    private final PaymentDao paymentDao;

    private final RetryableDirectPaymentAutomatonRunner retryableDirectPaymentAutomatonRunner;
    private final DirectPaymentProcessor directPaymentProcessor;
    private final OSGIServiceRegistration<RetryPluginApi> retryPluginRegistry;

    @Inject
    public DefaultPaymentApi(final PaymentMethodProcessor methodProcessor,
                             final PaymentProcessor paymentProcessor,
                             final RefundProcessor refundProcessor,
                             final InvoiceInternalApi invoiceApi,
                             final Clock clock,
                             final RetryableDirectPaymentAutomatonRunner retryableDirectPaymentAutomatonRunner,
                             final DirectPaymentProcessor directPaymentProcessor,
                             final InternalCallContextFactory internalCallContextFactory,
                             final PaymentConfig paymentConfig,
                             final PaymentDao paymentDao, final OSGIServiceRegistration<RetryPluginApi> retryPluginRegistry) {
        this.methodProcessor = methodProcessor;
        this.paymentProcessor = paymentProcessor;
        this.refundProcessor = refundProcessor;
        this.clock = clock;
        this.retryableDirectPaymentAutomatonRunner = retryableDirectPaymentAutomatonRunner;
        this.directPaymentProcessor = directPaymentProcessor;
        this.internalCallContextFactory = internalCallContextFactory;
        this.paymentDao = paymentDao;
        this.retryPluginRegistry = retryPluginRegistry;

        final OSGIServiceDescriptor desc = new OSGIServiceDescriptor() {
            @Override
            public String getPluginSymbolicName() {
                return null;
            }

            @Override
            public String getRegistrationName() {
                return INVOICE_PLUGIN_NAME;
            }
        };
        retryPluginRegistry.registerService(desc, new InvoiceRetryPluginApi(paymentConfig, invoiceApi, paymentDao, internalCallContextFactory, clock));

    }

    @Override
    public DirectPayment createPayment(final Account account, final UUID invoiceId,
                                       @Nullable final BigDecimal amount, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentApiException {

        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(account.getId(), context);

        return retryableDirectPaymentAutomatonRunner.run(true,
                                                         TransactionType.PURCHASE,
                                                         account,
                                                         account.getPaymentMethodId(),
                                                         null,
                                                         invoiceId.toString(),
                                                         UUID.randomUUID().toString(),
                                                         amount,
                                                         account.getCurrency(),
                                                         false,
                                                         properties,
                                                         INVOICE_PLUGIN_NAME,
                                                         context,
                                                         internalContext);
    }

    @Override
    public DirectPayment createExternalPayment(final Account account, final UUID invoiceId, final BigDecimal amount, final CallContext context) throws PaymentApiException {
        return paymentProcessor.createPayment(account, invoiceId, amount,
                                              true, true, ImmutableList.<PluginProperty>of(), context, internalCallContextFactory.createInternalCallContext(account.getId(), context)
                                             );
    }

    @Override
    public void notifyPendingPaymentOfStateChanged(final Account account, final UUID paymentId, final boolean isSuccess, final CallContext context) throws PaymentApiException {
        paymentProcessor.notifyPendingPaymentOfStateChanged(account, paymentId, isSuccess,
                                                            internalCallContextFactory.createInternalCallContext(account.getId(), context));
    }

    @Override
    public void notifyPaymentOfChargeback(final Account account, final UUID paymentId, final BigDecimal amount, final Currency currency, final CallContext callContext) throws PaymentApiException {
        // TODO Implement when merging with DirectPaymentApi
    }

    @Override
    public DirectPayment retryPayment(final Account account, final UUID paymentId, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentApiException {
        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), context);
        paymentProcessor.retryPaymentFromApi(paymentId, properties, context, internalCallContext);
        return getPayment(paymentId, false, properties, context);
    }

    @Override
    public Pagination<DirectPayment> getPayments(final Long offset, final Long limit, final Iterable<PluginProperty> properties, final TenantContext context) {
        return paymentProcessor.getPayments(offset, limit, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Pagination<DirectPayment> getPayments(final Long offset, final Long limit, final String pluginName, final Iterable<PluginProperty> properties, final TenantContext tenantContext) throws PaymentApiException {
        return paymentProcessor.getPayments(offset, limit, pluginName, properties, tenantContext, internalCallContextFactory.createInternalTenantContext(tenantContext));
    }

    @Override
    public DirectPayment getPayment(final UUID paymentId, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentApiException {
        final DirectPayment payment = paymentProcessor.getPayment(paymentId, withPluginInfo, properties, context, internalCallContextFactory.createInternalTenantContext(context));
        if (payment == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT, paymentId);
        }
        return payment;
    }

    @Override
    public Pagination<DirectPayment> searchPayments(final String searchKey, final Long offset, final Long limit, final Iterable<PluginProperty> properties, final TenantContext context) {
        return paymentProcessor.searchPayments(searchKey, offset, limit, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Pagination<DirectPayment> searchPayments(final String searchKey, final Long offset, final Long limit, final String pluginName, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentApiException {
        return paymentProcessor.searchPayments(searchKey, offset, limit, pluginName, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Pagination<DirectPayment> getRefunds(final Long offset, final Long limit, final Iterable<PluginProperty> properties, final TenantContext context) {
        return refundProcessor.getRefunds(offset, limit, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Pagination<DirectPayment> getRefunds(final Long offset, final Long limit, final String pluginName, final Iterable<PluginProperty> properties, final TenantContext tenantContext) throws PaymentApiException {
        return refundProcessor.getRefunds(offset, limit, pluginName, properties, tenantContext, internalCallContextFactory.createInternalTenantContext(tenantContext));
    }

    @Override
    public Pagination<DirectPayment> searchRefunds(final String searchKey, final Long offset, final Long limit, final Iterable<PluginProperty> properties, final TenantContext context) {
        return refundProcessor.searchRefunds(searchKey, offset, limit, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Pagination<DirectPayment> searchRefunds(final String searchKey, final Long offset, final Long limit, final String pluginName, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentApiException {
        return refundProcessor.searchRefunds(searchKey, offset, limit, pluginName, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public List<DirectPayment> getInvoicePayments(final UUID invoiceId, final TenantContext context) {
        return paymentProcessor.getInvoicePayments(invoiceId, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public List<DirectPayment> getAccountPayments(final UUID accountId, final TenantContext context)
            throws PaymentApiException {
        return paymentProcessor.getAccountPayments(accountId, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public DirectPayment getRefund(final UUID refundId, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentApiException {
        return refundProcessor.getRefund(refundId, withPluginInfo, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public DirectPayment createRefund(final Account account, final UUID paymentId, final BigDecimal refundAmount, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentApiException {
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentApiException(ErrorCode.PAYMENT_REFUND_AMOUNT_NEGATIVE_OR_NULL);
        }
        return refundProcessor.createRefund(account, paymentId, refundAmount, false, ImmutableMap.<UUID, BigDecimal>of(), properties,
                                            context, internalCallContextFactory.createInternalCallContext(account.getId(), context));
    }

    @Override
    public void notifyPendingRefundOfStateChanged(final Account account, final UUID refundId, final boolean isSuccess, final CallContext context) throws PaymentApiException {
        refundProcessor.notifyPendingRefundOfStateChanged(account, refundId, isSuccess,
                                                          internalCallContextFactory.createInternalCallContext(account.getId(), context));
    }

    @Override
    public DirectPayment createRefundWithAdjustment(final Account account, final UUID paymentId, final BigDecimal refundAmount, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentApiException {
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentApiException(ErrorCode.PAYMENT_REFUND_AMOUNT_NEGATIVE_OR_NULL);
        }
        return refundProcessor.createRefund(account, paymentId, refundAmount, true, ImmutableMap.<UUID, BigDecimal>of(), properties,
                                            context, internalCallContextFactory.createInternalCallContext(account.getId(), context));
    }

    @Override
    public DirectPayment createRefundWithItemsAdjustments(final Account account, final UUID paymentId, final Set<UUID> invoiceItemIds, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentApiException {
        final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts = new HashMap<UUID, BigDecimal>();
        for (final UUID invoiceItemId : invoiceItemIds) {
            invoiceItemIdsWithAmounts.put(invoiceItemId, null);
        }

        return refundProcessor.createRefund(account, paymentId, null, true, invoiceItemIdsWithAmounts, properties,
                                            context, internalCallContextFactory.createInternalCallContext(account.getId(), context));
    }

    @Override
    public DirectPayment createRefundWithItemsAdjustments(final Account account, final UUID paymentId, final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentApiException {
        return refundProcessor.createRefund(account, paymentId, null, true, invoiceItemIdsWithAmounts, properties,
                                            context, internalCallContextFactory.createInternalCallContext(account.getId(), context));
    }

    @Override
    public List<DirectPayment> getAccountRefunds(final Account account, final TenantContext context)
            throws PaymentApiException {
        return refundProcessor.getAccountRefunds(account, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public List<DirectPayment> getPaymentRefunds(final UUID paymentId, final TenantContext context)
            throws PaymentApiException {
        return refundProcessor.getPaymentRefunds(paymentId, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Set<String> getAvailablePlugins() {
        return methodProcessor.getAvailablePlugins();
    }

    @Override
    public UUID addPaymentMethod(final String pluginName, final Account account,
                                 final boolean setDefault, final PaymentMethodPlugin paymentMethodInfo,
                                 final Iterable<PluginProperty> properties, final CallContext context)
            throws PaymentApiException {
        return methodProcessor.addPaymentMethod(pluginName, account, setDefault, paymentMethodInfo, properties,
                                                context, internalCallContextFactory.createInternalCallContext(account.getId(), context));
    }

    @Override
    public List<PaymentMethod> getPaymentMethods(final Account account, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context)
            throws PaymentApiException {
        return methodProcessor.getPaymentMethods(account, withPluginInfo, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public PaymentMethod getPaymentMethodById(final UUID paymentMethodId, final boolean includedDeleted, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context)
            throws PaymentApiException {
        return methodProcessor.getPaymentMethodById(paymentMethodId, includedDeleted, withPluginInfo, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Pagination<PaymentMethod> getPaymentMethods(final Long offset, final Long limit, final Iterable<PluginProperty> properties, final TenantContext context) {
        return methodProcessor.getPaymentMethods(offset, limit, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Pagination<PaymentMethod> getPaymentMethods(final Long offset, final Long limit, final String pluginName, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentApiException {
        return methodProcessor.getPaymentMethods(offset, limit, pluginName, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Pagination<PaymentMethod> searchPaymentMethods(final String searchKey, final Long offset, final Long limit, final Iterable<PluginProperty> properties, final TenantContext context) {
        return methodProcessor.searchPaymentMethods(searchKey, offset, limit, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Pagination<PaymentMethod> searchPaymentMethods(final String searchKey, final Long offset, final Long limit, final String pluginName, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentApiException {
        return methodProcessor.searchPaymentMethods(searchKey, offset, limit, pluginName, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public void deletedPaymentMethod(final Account account, final UUID paymentMethodId, final boolean deleteDefaultPaymentMethodWithAutoPayOff, final Iterable<PluginProperty> properties, final CallContext context)
            throws PaymentApiException {
        methodProcessor.deletedPaymentMethod(account, paymentMethodId, deleteDefaultPaymentMethodWithAutoPayOff, properties, context, internalCallContextFactory.createInternalCallContext(account.getId(), context));
    }

    @Override
    public void setDefaultPaymentMethod(final Account account, final UUID paymentMethodId, final Iterable<PluginProperty> properties, final CallContext context)
            throws PaymentApiException {
        methodProcessor.setDefaultPaymentMethod(account, paymentMethodId, properties, context, internalCallContextFactory.createInternalCallContext(account.getId(), context));
    }

    @Override
    public List<PaymentMethod> refreshPaymentMethods(final String pluginName, final Account account, final Iterable<PluginProperty> properties, final CallContext context)
            throws PaymentApiException {
        return methodProcessor.refreshPaymentMethods(pluginName, account, properties, context, internalCallContextFactory.createInternalCallContext(account.getId(), context));
    }

    @Override
    public List<PaymentMethod> refreshPaymentMethods(final Account account, final Iterable<PluginProperty> properties, final CallContext context)
            throws PaymentApiException {
        final InternalCallContext callContext = internalCallContextFactory.createInternalCallContext(account.getId(), context);

        final List<PaymentMethod> paymentMethods = new LinkedList<PaymentMethod>();
        for (final String pluginName : methodProcessor.getAvailablePlugins()) {
            paymentMethods.addAll(methodProcessor.refreshPaymentMethods(pluginName, account, properties, context, callContext));
        }

        return paymentMethods;
    }

    private final static class InvoiceRetryPluginApi implements RetryPluginApi {

        private final PaymentConfig paymentConfig;
        private final InvoiceInternalApi invoiceApi;
        private final PaymentDao paymentDao;
        private final InternalCallContextFactory internalCallContextFactory;
        private final Clock clock;

        private final Logger logger = LoggerFactory.getLogger(InvoiceRetryPluginApi.class);

        private InvoiceRetryPluginApi(final PaymentConfig paymentConfig, final InvoiceInternalApi invoiceApi, final PaymentDao paymentDao,
                                      final InternalCallContextFactory internalCallContextFactory, final Clock clock) {
            this.paymentConfig = paymentConfig;
            this.invoiceApi = invoiceApi;
            this.paymentDao = paymentDao;
            this.internalCallContextFactory = internalCallContextFactory;
            this.clock = clock;
        }


        @Override
        public RetryPluginResult getRetryResult(final RetryPluginContext retryPluginContext) throws RetryPluginApiException {

            final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(retryPluginContext);
            final UUID invoiceId = UUID.fromString(retryPluginContext.getPaymentExternalKey());

            final BigDecimal requestedAmount;
            final DateTime nextRetryDate;
            try {
                final Invoice invoice = rebalanceAndGetInvoice(invoiceId, internalContext);
                if (invoice == null || invoice.isMigrationInvoice()) {
                    logger.error("Received invoice for payment that is a migration invoice - don't know how to handle those yet: {}", invoice);
                    return new DefaultRetryPluginResult(true);
                }
                requestedAmount = validateAndComputePaymentAmount(invoice, retryPluginContext.getAmount(), retryPluginContext.isApiPayment());
                nextRetryDate = computeNextRetryDate(retryPluginContext.getPaymentExternalKey(), internalContext);
            } catch (InvoiceApiException e) {
                return new DefaultRetryPluginResult(true);
            }
            return new DefaultRetryPluginResult(false, nextRetryDate, requestedAmount);
        }

        private DateTime computeNextRetryDate(final String paymentExternalKey, final InternalCallContext internalContext) {
            final List<DirectPaymentTransactionModelDao> purchasedTransactions = getPurchasedTransactions(paymentExternalKey, internalContext);
            if (purchasedTransactions.size() == 0) {
                return null;
            }
            final DirectPaymentTransactionModelDao lastTransaction = purchasedTransactions.get(purchasedTransactions.size() - 1);
            switch(lastTransaction.getPaymentStatus()) {
                case PAYMENT_FAILURE:
                    return getNextRetryDateForPaymentFailure(purchasedTransactions);

                case UNKNOWN:
                case PLUGIN_FAILURE:
                    return getNextRetryDateForPluginFailure(purchasedTransactions);

                default:
                    return null;
            }
        }


        private DateTime getNextRetryDateForPaymentFailure(final List<DirectPaymentTransactionModelDao> purchasedTransactions) {

            DateTime result = null;
            final List<Integer> retryDays = paymentConfig.getPaymentRetryDays();
            final int retryCount = getNumberAttemptsInState(purchasedTransactions, PaymentStatus.PAYMENT_FAILURE);
            if (retryCount < retryDays.size()) {
                int retryInDays = 0;
                final DateTime nextRetryDate = clock.getUTCNow();
                try {
                    retryInDays = retryDays.get(retryCount);
                    result = nextRetryDate.plusDays(retryInDays);
                } catch (NumberFormatException ex) {
                    logger.error("Could not get retry day for retry count {}", retryCount);
                }
            }
            return result;
        }

        private DateTime getNextRetryDateForPluginFailure(final List<DirectPaymentTransactionModelDao> purchasedTransactions) {

            final int retryAttempt = getNumberAttemptsInState(purchasedTransactions, PaymentStatus.PAYMENT_FAILURE);
            if (retryAttempt > paymentConfig.getPluginFailureRetryMaxAttempts()) {
                return null;
            }
            int nbSec = paymentConfig.getPluginFailureRetryStart();
            int remainingAttempts = retryAttempt;
            while (--remainingAttempts > 0) {
                nbSec = nbSec * paymentConfig.getPluginFailureRetryMultiplier();
            }
            return clock.getUTCNow().plusSeconds(nbSec);
        }


        private int getNumberAttemptsInState(final Collection<DirectPaymentTransactionModelDao> allTransactions, final PaymentStatus... statuses) {
            if (allTransactions == null || allTransactions.size() == 0) {
                return 0;
            }
            return Collections2.filter(allTransactions, new Predicate<DirectPaymentTransactionModelDao>() {
                @Override
                public boolean apply(final DirectPaymentTransactionModelDao input) {
                    for (final PaymentStatus cur : statuses) {
                        if (input.getPaymentStatus() == cur) {
                            return true;
                        }
                    }
                    return false;
                }
            }).size();
        }

        private List<DirectPaymentTransactionModelDao> getPurchasedTransactions(final String paymentExternalKey, final InternalCallContext internalContext) {
            final DirectPaymentModelDao payment = paymentDao.getDirectPaymentByExternalKey(paymentExternalKey, internalContext);
            if (payment == null) {
                return Collections.emptyList();
            }
            final List<DirectPaymentTransactionModelDao> transactions =  paymentDao.getDirectTransactionsForDirectPayment(payment.getId(), internalContext);
            if (transactions == null || transactions.size() == 0) {
                return Collections.emptyList();
            }
            return ImmutableList.copyOf(Iterables.filter(transactions, new Predicate<DirectPaymentTransactionModelDao>() {
                @Override
                public boolean apply(final DirectPaymentTransactionModelDao input) {
                    return input.getTransactionType() == TransactionType.PURCHASE;
                }
            }));
        }


        private Invoice rebalanceAndGetInvoice(final UUID invoiceId, final InternalCallContext context) throws InvoiceApiException {
            final Invoice invoicePriorRebalancing = invoiceApi.getInvoiceById(invoiceId, context);
            invoiceApi.consumeExistingCBAOnAccountWithUnpaidInvoices(invoicePriorRebalancing.getAccountId(), context);
            final Invoice invoice = invoiceApi.getInvoiceById(invoiceId, context);
            return invoice;
        }

        private BigDecimal validateAndComputePaymentAmount(final Invoice invoice, @Nullable final BigDecimal inputAmount, final boolean isInstantPayment)
                throws RetryPluginApiException {

            if (invoice.getBalance().compareTo(BigDecimal.ZERO) <= 0) {
                throw new RetryPluginApiException("Invoice " + invoice.getId() + " has already been paid");
            }
            if (isInstantPayment &&
                inputAmount != null &&
                invoice.getBalance().compareTo(inputAmount) < 0) {
                throw new RetryPluginApiException("Invoice " + invoice.getId() +
                                              " has a balance of " + invoice.getBalance().floatValue() +
                                               " less than retry payment amount of " + inputAmount.floatValue());
            }
            return inputAmount != null ? inputAmount : invoice.getBalance();
        }

    }
}
